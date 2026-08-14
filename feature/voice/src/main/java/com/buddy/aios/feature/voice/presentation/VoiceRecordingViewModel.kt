package com.buddy.aios.feature.voice.presentation

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.StorageLocation
import com.buddy.aios.core.domain.entity.VoiceRecording
import com.buddy.aios.core.domain.repository.IVoiceRecordingRepository
import com.buddy.aios.feature.voice.recorder.RecorderState
import com.buddy.aios.feature.voice.recorder.VoiceRecorder
import com.buddy.aios.feature.voice.di.VoicePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class VoiceRecordingUiState(
    val isVoiceRecordingEnabled: Boolean = false,
    val storageLocation: StorageLocation = StorageLocation.PRIVATE,
    val recordings: List<VoiceRecording> = emptyList(),
    val recorderState: RecorderState = RecorderState.Idle,
    val pendingSavePath: String? = null,
    val pendingDurationMs: Long = 0L,
    val showSaveDialog: Boolean = false,
    val showStoragePicker: Boolean = false,
    val recordingCount: Int = 0,
    val storageTotalBytes: Long = 0L,
    val message: String? = null,
    val isLoading: Boolean = false,
)

private const val PREF_VOICE_RECORDING_ENABLED = "voice_recording_enabled"
private const val PREF_STORAGE_LOCATION = "voice_storage_location"

@HiltViewModel
class VoiceRecordingViewModel @Inject constructor(
    private val repository: IVoiceRecordingRepository,
    private val voiceRecorder: VoiceRecorder,
    @VoicePrefs private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceRecordingUiState())
    val uiState: StateFlow<VoiceRecordingUiState> = _uiState.asStateFlow()

    init {
        loadPrefs()
        observeRecorderState()
        observeRecordings()
        loadStorageStats()
    }

    private fun loadPrefs() {
        val enabled = prefs.getBoolean(PREF_VOICE_RECORDING_ENABLED, false)
        val locationName = prefs.getString(PREF_STORAGE_LOCATION, StorageLocation.PRIVATE.name)
        val location = try {
            StorageLocation.valueOf(locationName ?: StorageLocation.PRIVATE.name)
        } catch (e: Exception) { StorageLocation.PRIVATE }
        _uiState.value = _uiState.value.copy(
            isVoiceRecordingEnabled = enabled,
            storageLocation = location,
        )
    }

    private fun observeRecorderState() {
        voiceRecorder.state.onEach { state ->
            _uiState.value = _uiState.value.copy(recorderState = state)
        }.launchIn(viewModelScope)
    }

    private fun observeRecordings() {
        repository.getRecordings().onEach { list ->
            _uiState.value = _uiState.value.copy(recordings = list)
        }.launchIn(viewModelScope)
    }

    private fun loadStorageStats() {
        viewModelScope.launch {
            val stats = repository.getStorageStats()
            _uiState.value = _uiState.value.copy(
                recordingCount = stats.count,
                storageTotalBytes = stats.totalBytes,
            )
        }
    }

    // ── Recording Enable/Disable ─────────────────────────────────────────────

    fun onToggleVoiceRecording(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_VOICE_RECORDING_ENABLED, enabled).apply()
        if (enabled && !prefs.contains(PREF_STORAGE_LOCATION)) {
            // First time enable — show storage picker
            _uiState.value = _uiState.value.copy(
                isVoiceRecordingEnabled = true,
                showStoragePicker = true,
            )
        } else {
            _uiState.value = _uiState.value.copy(isVoiceRecordingEnabled = enabled)
        }
    }

    fun onStorageLocationSelected(location: StorageLocation) {
        prefs.edit().putString(PREF_STORAGE_LOCATION, location.name).apply()
        _uiState.value = _uiState.value.copy(
            storageLocation = location,
            showStoragePicker = false,
        )
    }

    fun onChangeStorageLocation() {
        _uiState.value = _uiState.value.copy(showStoragePicker = true)
    }

    fun onDismissStoragePicker() {
        _uiState.value = _uiState.value.copy(showStoragePicker = false)
    }

    // ── Recording Actions ────────────────────────────────────────────────────

    fun onStartRecording() {
        if (!_uiState.value.isVoiceRecordingEnabled) return
        voiceRecorder.startRecording()
        AppLogger.d(TAG, "Recording started by user")
    }

    fun onStopRecording() {
        val result = voiceRecorder.stopRecording() ?: return
        val (filePath, durationMs) = result
        _uiState.value = _uiState.value.copy(
            pendingSavePath = filePath,
            pendingDurationMs = durationMs,
            showSaveDialog = true,
        )
    }

    fun onSaveRecording(customTitle: String? = null) {
        val path = _uiState.value.pendingSavePath ?: return
        val durationMs = _uiState.value.pendingDurationMs
        val location = _uiState.value.storageLocation

        val title = customTitle?.takeIf { it.isNotBlank() }
            ?: generateDefaultTitle()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.saveRecording(
                    tempFilePath = path,
                    title = title,
                    durationMs = durationMs,
                    location = location,
                )
                loadStorageStats()
                _uiState.value = _uiState.value.copy(
                    showSaveDialog = false,
                    pendingSavePath = null,
                    pendingDurationMs = 0L,
                    isLoading = false,
                    message = "Recording saved.",
                )
                voiceRecorder.resetToIdle()
                AppLogger.d(TAG, "Recording saved successfully")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to save recording", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Failed to save recording: ${e.message}",
                )
            }
        }
    }

    fun onDeleteTempRecording() {
        val path = _uiState.value.pendingSavePath ?: return
        voiceRecorder.deleteTempFile(path)
        _uiState.value = _uiState.value.copy(
            showSaveDialog = false,
            pendingSavePath = null,
            pendingDurationMs = 0L,
            message = "Recording deleted.",
        )
    }

    fun onDismissSaveDialog() {
        // User dismissed — treat as delete (temp file cleanup)
        onDeleteTempRecording()
    }

    // ── Saved Recording Actions ──────────────────────────────────────────────

    fun onDeleteSavedRecording(id: String) {
        viewModelScope.launch {
            val success = repository.deleteRecording(id)
            loadStorageStats()
            _uiState.value = _uiState.value.copy(
                message = if (success) "Recording deleted." else "Could not delete recording.",
            )
        }
    }

    fun onMessageShown() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun generateDefaultTitle(): String {
        val sdf = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        return "Voice Recording — ${sdf.format(Date())}"
    }

    private companion object {
        const val TAG = "VoiceRecordingVM"
    }
}
