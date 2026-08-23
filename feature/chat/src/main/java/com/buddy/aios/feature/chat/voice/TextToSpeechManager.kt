package com.buddy.aios.feature.chat.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.canVoiceOutput
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.common.voice.IVoiceOutputManager
import com.buddy.aios.core.ui.island.AIOSIslandState
import com.buddy.aios.core.ui.island.AIOSIslandStateManager
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
 * Implements [IVoiceOutputManager] so lower-level modules (workers) can trigger speech via the
 * abstraction without depending on feature:chat.
 */
@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val buddyModeRepository: IBuddyModeRepository,
    private val islandStateManager: AIOSIslandStateManager,
) : TextToSpeech.OnInitListener, IVoiceOutputManager {

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
            var result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                AppLogger.w(TAG, "Default locale (${Locale.getDefault()}) TTS data missing (code=$result). Falling back to Locale.US")
                result = tts?.setLanguage(Locale.US)
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                AppLogger.w(TAG, "Locale.US TTS data missing (code=$result). Falling back to Locale.ENGLISH")
                result = tts?.setLanguage(Locale.ENGLISH)
            }

            val isSupported = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            isInitialized = isSupported

            if (isSupported) {
                tts?.setSpeechRate(0.85f)
                tts?.setPitch(1.0f)
                tts?.setOnUtteranceProgressListener(createProgressListener())
                AppLogger.d(TAG, "TextToSpeech initialized successfully (isInitialized=true, speechRate=0.85f)")

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

    override fun speak(text: String) {
        if (!currentBuddyMode.canVoiceOutput) {
            AppLogger.w(TAG, "Speech output blocked: BuddyMode is $currentBuddyMode")
            _state.value = TextToSpeechState.Disabled
            return
        }

        val textAvailable = text.isNotBlank()
        AppLogger.d(TAG, "VoiceOutput: textAvailable=$textAvailable, textLength=${text.length}, isInitialized=$isInitialized")

        if (!isInitialized || !textAvailable) {
            AppLogger.w(TAG, "Cannot speak: initialized=$isInitialized, textAvailable=$textAvailable")
            return
        }

        // Silently flush any ongoing utterance WITHOUT emitting Idle state.
        // Emitting Idle here would cause VoiceConversationSession to see TTS-done and
        // immediately schedule listening restart before the new utterance even begins.
        flushTtsEngine()

        try {
            val utteranceId = "buddy_tts_${System.currentTimeMillis()}"
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result == TextToSpeech.SUCCESS) {
                _state.value = TextToSpeechState.Speaking(text)
                islandStateManager.show(
                    state = AIOSIslandState.SPEAKING,
                    message = "🔊 Speaking...",
                    autoDismissMs = 0L,
                )
                AppLogger.d(TAG, "VoiceOutput: speak() result=SUCCESS for ${text.length} chars")
            } else {
                AppLogger.w(TAG, "VoiceOutput: speak() result=ERROR code $result")
                _state.value = TextToSpeechState.Error("Failed to synthesize speech")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error speaking text", e)
            _state.value = TextToSpeechState.Error("Speech synthesis exception")
        }
    }

    /**
     * Stop any active speech and emit [TextToSpeechState.Idle].
     * Called by external owners (VoiceConversationSession.stopSession, toggleVoiceInput).
     */
    override fun stop() {
        flushTtsEngine()
        if (_state.value is TextToSpeechState.Speaking) {
            _state.value = TextToSpeechState.Idle
        }
        dismissIslandIfSpeaking()
    }

    /**
     * Silently interrupt the Android TTS engine without changing [_state].
     * Used internally by [speak] to flush a previous utterance before starting a new one,
     * avoiding a spurious Idle emission that would confuse VoiceConversationSession.
     */
    private fun flushTtsEngine() {
        try {
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "flushTtsEngine error: ${e.message}")
        }
    }

    private fun dismissIslandIfSpeaking() {
        if (islandStateManager.displayState.value.state == AIOSIslandState.SPEAKING) {
            islandStateManager.dismiss()
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
            dismissIslandIfSpeaking()
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
            dismissIslandIfSpeaking()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            AppLogger.w(TAG, "TTS onError: $utteranceId")
            _state.value = TextToSpeechState.Error("Speech playback interrupted")
            dismissIslandIfSpeaking()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            AppLogger.w(TAG, "TTS onError: $utteranceId code=$errorCode")
            _state.value = TextToSpeechState.Error("Speech error ($errorCode)")
            dismissIslandIfSpeaking()
        }
    }

    private companion object {
        const val TAG = "TextToSpeechManager"
    }
}
