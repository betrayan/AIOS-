package com.buddy.aios.feature.chat.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.canVoiceOutput
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

sealed interface TextToSpeechState {
    data object Idle : TextToSpeechState
    data class Speaking(val text: String) : TextToSpeechState
    data class Error(val message: String) : TextToSpeechState
    data object Disabled : TextToSpeechState
}

/**
 * Production-ready Android [TextToSpeech] engine wrapper with state management & BuddyMode compliance.
 */
@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val buddyModeRepository: IBuddyModeRepository,
) : TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _state = MutableStateFlow<TextToSpeechState>(TextToSpeechState.Idle)
    val state: StateFlow<TextToSpeechState> = _state.asStateFlow()

    private var currentBuddyMode: BuddyMode = BuddyMode.ACTIVE

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to instantiate TextToSpeech", e)
            _state.value = TextToSpeechState.Error("Text-to-speech engine unavailable")
        }

        scope.launch {
            buddyModeRepository.observeBuddyMode().collect { mode ->
                currentBuddyMode = mode
                AppLogger.d(TAG, "Observed BuddyMode change in TTS: $mode")
                if (!mode.canVoiceOutput) {
                    stop()
                    _state.value = TextToSpeechState.Disabled
                } else if (_state.value is TextToSpeechState.Disabled) {
                    _state.value = TextToSpeechState.Idle
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            val isSupported = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            isInitialized = isSupported

            if (isSupported) {
                tts?.setSpeechRate(0.85f)
                tts?.setPitch(1.0f)
                tts?.setOnUtteranceProgressListener(createProgressListener())
                AppLogger.d(TAG, "TextToSpeech initialized successfully with speechRate=0.85f")

                if (currentBuddyMode.canVoiceOutput) {
                    _state.value = TextToSpeechState.Idle
                }
            } else {
                AppLogger.w(TAG, "TextToSpeech language missing or not supported (code=$result)")
                _state.value = TextToSpeechState.Error("Language not supported for speech synthesis")
            }
        } else {
            AppLogger.w(TAG, "TextToSpeech initialization failed with status: $status")
            isInitialized = false
            _state.value = TextToSpeechState.Error("Failed to initialize speech engine")
        }
    }

    fun speak(text: String) {
        if (!currentBuddyMode.canVoiceOutput) {
            AppLogger.w(TAG, "Speech output blocked: BuddyMode is $currentBuddyMode")
            _state.value = TextToSpeechState.Disabled
            return
        }

        if (!isInitialized || text.isBlank()) {
            AppLogger.w(TAG, "Cannot speak: initialized=$isInitialized, textLength=${text.length}")
            return
        }

        stop() // Interrupt any ongoing utterance — single active TTS request

        try {
            val utteranceId = "buddy_tts_${System.currentTimeMillis()}"
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result == TextToSpeech.SUCCESS) {
                _state.value = TextToSpeechState.Speaking(text)
                AppLogger.d(TAG, "TTS speaking started for ${text.length} chars")
            } else {
                AppLogger.w(TAG, "TTS speak() returned error code $result")
                _state.value = TextToSpeechState.Error("Failed to synthesize speech")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error speaking text", e)
            _state.value = TextToSpeechState.Error("Speech synthesis exception")
        }
    }

    fun stop() {
        try {
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error stopping TextToSpeech: ${e.message}")
        } finally {
            if (_state.value is TextToSpeechState.Speaking) {
                _state.value = TextToSpeechState.Idle
            }
        }
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error shutting down TextToSpeech: ${e.message}")
        } finally {
            tts = null
            isInitialized = false
            _state.value = TextToSpeechState.Idle
        }
    }

    private fun createProgressListener(): UtteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            AppLogger.d(TAG, "TTS onStart: $utteranceId")
        }

        override fun onDone(utteranceId: String?) {
            AppLogger.d(TAG, "TTS onDone: $utteranceId")
            if (_state.value is TextToSpeechState.Speaking) {
                _state.value = TextToSpeechState.Idle
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            AppLogger.w(TAG, "TTS onError: $utteranceId")
            _state.value = TextToSpeechState.Error("Speech playback interrupted")
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            AppLogger.w(TAG, "TTS onError: $utteranceId code=$errorCode")
            _state.value = TextToSpeechState.Error("Speech error ($errorCode)")
        }
    }

    private companion object {
        const val TAG = "TextToSpeechManager"
    }
}
