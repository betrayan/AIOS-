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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState
    data class PartialResult(val text: String) : VoiceState
    data class FinalResult(val text: String) : VoiceState
    data class Error(val message: String) : VoiceState
}

/**
 * Production-ready Android [SpeechRecognizer] lifecycle & state manager.
 */
@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening() {
        if (!isPermissionGranted()) {
            _voiceState.value = VoiceState.Error("Microphone permission required")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceState.value = VoiceState.Error("Speech recognition unavailable on this device")
            return
        }

        stopListening() // Cleanup any ongoing instance safely

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
            _voiceState.value = VoiceState.Listening
            AppLogger.d(TAG, "SpeechRecognizer started listening")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start SpeechRecognizer", e)
            _voiceState.value = VoiceState.Error("Couldn't start microphone")
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
            if (_voiceState.value is VoiceState.Listening) {
                _voiceState.value = VoiceState.Idle
            }
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
        }

        override fun onError(error: Int) {
            val userMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client speech error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network error during speech"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout during speech"
                SpeechRecognizer.ERROR_NO_MATCH -> "Couldn't hear that. Try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Microphone is busy. Try again."
                SpeechRecognizer.ERROR_SERVER -> "Speech server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                else -> "Speech recognition failed"
            }
            AppLogger.w(TAG, "SpeechRecognizer onError code=$error ($userMessage)")
            _voiceState.value = VoiceState.Error(userMessage)
            stopListening()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim().orEmpty()
            AppLogger.d(TAG, "onResults text: $text")
            if (text.isNotBlank()) {
                _voiceState.value = VoiceState.FinalResult(text)
            } else {
                _voiceState.value = VoiceState.Error("Couldn't hear that. Try again.")
            }
            stopListening()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim().orEmpty()
            if (text.isNotBlank()) {
                _voiceState.value = VoiceState.PartialResult(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private companion object {
        const val TAG = "VoiceManager"
    }
}
