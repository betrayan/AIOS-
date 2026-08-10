package com.buddy.aios.feature.chat.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.canVoiceInput
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VoiceInputState {
    data object Idle : VoiceInputState
    data object Listening : VoiceInputState
    data class PartialResult(val text: String) : VoiceInputState
    data class FinalResult(val text: String) : VoiceInputState
    data class Processing(val text: String) : VoiceInputState
    data class Error(val message: String) : VoiceInputState
    data object Disabled : VoiceInputState
}

/**
 * Production-ready voice input lifecycle manager adhering to BuddyMode rules.
 */
@Singleton
class VoiceInputManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val buddyModeRepository: IBuddyModeRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private var currentBuddyMode: BuddyMode = BuddyMode.ACTIVE

    init {
        scope.launch {
            buddyModeRepository.observeBuddyMode().collect { mode ->
                currentBuddyMode = mode
                AppLogger.d(TAG, "Observed BuddyMode change: $mode")
                if (!mode.canVoiceInput) {
                    stopListening()
                    _state.value = VoiceInputState.Disabled
                } else if (_state.value is VoiceInputState.Disabled) {
                    _state.value = VoiceInputState.Idle
                }
            }
        }
    }

    fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening() {
        if (!currentBuddyMode.canVoiceInput) {
            AppLogger.w(TAG, "Voice input blocked: BuddyMode is $currentBuddyMode")
            _state.value = VoiceInputState.Disabled
            return
        }

        if (!isPermissionGranted()) {
            AppLogger.w(TAG, "Voice input failed: RECORD_AUDIO permission missing")
            _state.value = VoiceInputState.Error("Microphone permission required")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            AppLogger.w(TAG, "Voice input failed: SpeechRecognizer unavailable")
            _state.value = VoiceInputState.Error("Speech recognition unavailable on this device")
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
            _state.value = VoiceInputState.Listening
            AppLogger.d(TAG, "SpeechRecognizer started listening successfully")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start SpeechRecognizer", e)
            _state.value = VoiceInputState.Error("Couldn't start microphone")
            stopListening()
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error destroying SpeechRecognizer: ${e.message}")
        } finally {
            speechRecognizer = null
            if (_state.value is VoiceInputState.Listening || _state.value is VoiceInputState.PartialResult) {
                _state.value = VoiceInputState.Idle
            }
        }
    }

    fun cancel() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error cancelling SpeechRecognizer: ${e.message}")
        } finally {
            speechRecognizer = null
            _state.value = VoiceInputState.Idle
        }
    }

    fun resetState() {
        if (!currentBuddyMode.canVoiceInput) {
            _state.value = VoiceInputState.Disabled
        } else {
            _state.value = VoiceInputState.Idle
        }
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            AppLogger.d(TAG, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            AppLogger.d(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            AppLogger.d(TAG, "onEndOfSpeech")
            if (_state.value is VoiceInputState.Listening || _state.value is VoiceInputState.PartialResult) {
                val currentText = (_state.value as? VoiceInputState.PartialResult)?.text.orEmpty()
                if (currentText.isNotBlank()) {
                    _state.value = VoiceInputState.Processing(currentText)
                }
            }
        }

        override fun onError(error: Int) {
            val userMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client speech error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout during speech"
                SpeechRecognizer.ERROR_NO_MATCH -> "Couldn't hear that. Please try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Microphone is busy"
                SpeechRecognizer.ERROR_SERVER -> "Speech server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                else -> "Speech recognition error ($error)"
            }
            AppLogger.w(TAG, "SpeechRecognizer onError: code=$error msg=$userMessage")
            _state.value = VoiceInputState.Error(userMessage)
            stopListening()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim().orEmpty()
            AppLogger.d(TAG, "onResults text: $text")
            if (text.isNotBlank()) {
                _state.value = VoiceInputState.FinalResult(text)
            } else {
                _state.value = VoiceInputState.Error("Couldn't hear that. Please try again.")
            }
            stopListening()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim().orEmpty()
            if (text.isNotBlank()) {
                _state.value = VoiceInputState.PartialResult(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private companion object {
        const val TAG = "VoiceInputManager"
    }
}
