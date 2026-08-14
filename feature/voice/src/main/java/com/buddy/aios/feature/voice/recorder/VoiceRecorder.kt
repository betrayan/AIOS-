package com.buddy.aios.feature.voice.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.buddy.aios.core.common.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RecorderState {
    data object Idle : RecorderState
    data class Recording(val filePath: String, val startTimeMs: Long) : RecorderState
    data class Stopped(val filePath: String, val durationMs: Long) : RecorderState
    data class Error(val message: String) : RecorderState
}

/**
 * Manages audio capture using [MediaRecorder].
 * Writes to a temp file in cacheDir/voice_temp/ until the user decides to SAVE or DELETE.
 *
 * This pipeline is COMPLETELY INDEPENDENT from:
 * - VoiceManager (SpeechRecognizer — text only)
 * - VoiceInputManager (SpeechRecognizer — BuddyMode-aware)
 * - TextToSpeechManager (TTS output)
 */
@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var mediaRecorder: MediaRecorder? = null
    private var startTimeMs: Long = 0L

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val tempDir: File
        get() = File(context.cacheDir, "voice_temp").also { it.mkdirs() }

    val isRecording: Boolean
        get() = _state.value is RecorderState.Recording

    /**
     * Start recording audio to a new temp file.
     * @return The absolute path of the temp file being written to.
     */
    fun startRecording(): String {
        if (isRecording) {
            AppLogger.w(TAG, "startRecording called while already recording — stopping first")
            stopRecording()
        }

        val fileName = "voice_temp_${UUID.randomUUID()}.m4a"
        val outputFile = File(tempDir, fileName)

        mediaRecorder = createMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFile.absolutePath)
            try {
                prepare()
                start()
                startTimeMs = System.currentTimeMillis()
                _state.value = RecorderState.Recording(outputFile.absolutePath, startTimeMs)
                AppLogger.d(TAG, "Recording started: ${outputFile.name}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start MediaRecorder", e)
                _state.value = RecorderState.Error("Failed to start recording: ${e.message}")
                release()
            }
        }

        return outputFile.absolutePath
    }

    /**
     * Stop the current recording.
     * @return The temp file path and duration, or null if not recording.
     */
    fun stopRecording(): Pair<String, Long>? {
        val current = _state.value as? RecorderState.Recording ?: run {
            AppLogger.w(TAG, "stopRecording called but not in Recording state")
            return null
        }

        return try {
            mediaRecorder?.stop()
            val durationMs = System.currentTimeMillis() - current.startTimeMs
            _state.value = RecorderState.Stopped(current.filePath, durationMs)
            AppLogger.d(TAG, "Recording stopped. Duration: ${durationMs}ms")
            release()
            Pair(current.filePath, durationMs)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to stop MediaRecorder", e)
            _state.value = RecorderState.Error("Failed to stop recording: ${e.message}")
            release()
            // Try to return partial file if it exists
            if (File(current.filePath).exists()) {
                Pair(current.filePath, System.currentTimeMillis() - current.startTimeMs)
            } else null
        }
    }

    /**
     * Cancel the current recording and delete the temp file.
     */
    fun cancelRecording() {
        val current = _state.value as? RecorderState.Recording
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Cancel stop error (expected on some devices): ${e.message}")
        } finally {
            release()
            current?.let { File(it.filePath).delete() }
            _state.value = RecorderState.Idle
            AppLogger.d(TAG, "Recording cancelled and temp file deleted")
        }
    }

    /**
     * Delete a temp file that was not saved.
     */
    fun deleteTempFile(filePath: String) {
        try {
            val deleted = File(filePath).delete()
            AppLogger.d(TAG, "deleteTempFile path=$filePath deleted=$deleted")
        } catch (e: Exception) {
            AppLogger.w(TAG, "deleteTempFile error: ${e.message}")
        }
        if (_state.value is RecorderState.Stopped) {
            _state.value = RecorderState.Idle
        }
    }

    fun resetToIdle() {
        _state.value = RecorderState.Idle
    }

    private fun release() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            AppLogger.w(TAG, "MediaRecorder release error: ${e.message}")
        } finally {
            mediaRecorder = null
        }
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

    private companion object {
        const val TAG = "VoiceRecorder"
    }
}
