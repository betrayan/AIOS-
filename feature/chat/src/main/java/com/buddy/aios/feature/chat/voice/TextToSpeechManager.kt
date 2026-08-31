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
                AppLogger.w(TAG, "TTS onInit: default locale (${Locale.getDefault()}) data missing (code=$result). Falling back to Locale.US")
                result = tts?.setLanguage(Locale.US)
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                AppLogger.w(TAG, "TTS onInit: Locale.US data missing (code=$result). Falling back to Locale.ENGLISH")
                result = tts?.setLanguage(Locale.ENGLISH)
            }

            val isSupported = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            isInitialized = isSupported

            if (isSupported) {
                tts?.setSpeechRate(0.85f)
                tts?.setPitch(1.0f)
                tts?.setOnUtteranceProgressListener(createProgressListener())
                AppLogger.d(TAG, "TTS onInit: SUCCESS — isInitialized=true, speechRate=0.85f, buddyMode=$currentBuddyMode")

                if (currentBuddyMode.canVoiceOutput) {
                    _state.value = TextToSpeechState.Idle
                }
            } else {
                AppLogger.w(TAG, "TTS onInit: language not supported (code=$result) — voice output disabled")
                _state.value = TextToSpeechState.Error("Language not supported for speech synthesis")
            }
        } else {
            AppLogger.w(TAG, "TTS onInit: FAILED with status=$status")
            isInitialized = false
            _state.value = TextToSpeechState.Error("Failed to initialize speech engine")
        }
    }

    /**
     * Speak [text] aloud. Safe to call from ANY thread (IO, background, Main).
     * All TTS engine interaction and state updates are dispatched to Main.
     */
    override fun speak(text: String) {
        // Dispatch all TTS operations to Main thread. tts.speak() must run on the
        // thread that owns the TTS engine (which is the Main thread from init).
        scope.launch {
            speakOnMain(text)
        }
    }

    /**
     * Internal implementation — must only be called from [scope] (Main thread).
     */
    private fun speakOnMain(text: String) {
        val textAvailable = text.isNotBlank()
        AppLogger.d(TAG, "VoiceOutput: speak() requested — textAvailable=$textAvailable, chars=${text.length}, isInitialized=$isInitialized, buddyMode=$currentBuddyMode, voiceSessionMode=see VoiceConversationSession")

        if (!currentBuddyMode.canVoiceOutput) {
            AppLogger.w(TAG, "VoiceOutput: BLOCKED by BuddyMode=$currentBuddyMode")
            _state.value = TextToSpeechState.Disabled
            return
        }

        if (!isInitialized) {
            AppLogger.w(TAG, "VoiceOutput: BLOCKED — TTS not yet initialized (isInitialized=false). speak() was called before onInit completed.")
            return
        }

        if (!textAvailable) {
            AppLogger.w(TAG, "VoiceOutput: BLOCKED — text is empty/blank")
            return
        }

        // Silently flush any ongoing utterance WITHOUT emitting Idle state.
        // Emitting Idle here would cause VoiceConversationSession to see TTS-done and
        // immediately schedule listening restart before the new utterance even begins.
        flushTtsEngine()

        try {
            val utteranceId = "buddy_tts_${System.currentTimeMillis()}"
            AppLogger.d(TAG, "VoiceOutput: calling tts.speak() — utteranceId=$utteranceId")
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result == TextToSpeech.SUCCESS) {
                _state.value = TextToSpeechState.Speaking(text)
                islandStateManager.show(
                    state = AIOSIslandState.SPEAKING,
                    message = "🔊 Speaking...",
                    autoDismissMs = 0L,
                )
                AppLogger.d(TAG, "VoiceOutput: speak() SUCCESS — utteranceId=$utteranceId, chars=${text.length}")
            } else {
                AppLogger.w(TAG, "VoiceOutput: speak() FAILED — tts.speak() returned code=$result")
                _state.value = TextToSpeechState.Error("Failed to synthesize speech")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "VoiceOutput: speak() EXCEPTION", e)
            _state.value = TextToSpeechState.Error("Speech synthesis exception")
        }
    }

    /**
     * Stop any active speech and emit [TextToSpeechState.Idle].
     * Safe to call from ANY thread — dispatches to Main.
     */
    override fun stop() {
        scope.launch {
            AppLogger.d(TAG, "VoiceOutput: stop() called — currentState=${_state.value}")
            flushTtsEngine()
            if (_state.value is TextToSpeechState.Speaking) {
                _state.value = TextToSpeechState.Idle
            }
            dismissIslandIfSpeaking()
        }
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
        // UtteranceProgressListener callbacks fire on an arbitrary (non-main) background thread.
        // All StateFlow writes and islandStateManager calls MUST be posted back to Main.

        override fun onStart(utteranceId: String?) {
            AppLogger.d(TAG, "TTS onStart: utteranceId=$utteranceId")
            // onStart is informational — no state write needed here.
        }

        override fun onDone(utteranceId: String?) {
            AppLogger.d(TAG, "TTS onDone: utteranceId=$utteranceId — posting Idle to Main thread")
            // Post to Main: StateFlow writes and islandStateManager must run on Main.
            scope.launch {
                if (_state.value is TextToSpeechState.Speaking) {
                    _state.value = TextToSpeechState.Idle
                    AppLogger.d(TAG, "TTS onDone: state → Idle")
                }
                dismissIslandIfSpeaking()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            // The deprecated onError(utteranceId) is called by the OS when tts.stop() is
            // called externally (e.g., by flushTtsEngine() or stop()). This is an intentional
            // flush, NOT a real error — treat it as Idle so the voice pipeline continues normally.
            AppLogger.d(TAG, "TTS onError (deprecated): utteranceId=$utteranceId — treated as intentional flush → Idle")
            scope.launch {
                if (_state.value is TextToSpeechState.Speaking) {
                    _state.value = TextToSpeechState.Idle
                }
                dismissIslandIfSpeaking()
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            // ERROR_STOPPED (code 4) is produced by tts.stop() — intentional flush, not a real error.
            // All other codes are genuine TTS engine failures.
            val isIntentionalStop = (errorCode == TextToSpeech.ERROR)
            AppLogger.w(TAG, "TTS onError: utteranceId=$utteranceId, errorCode=$errorCode, intentionalStop=$isIntentionalStop")
            scope.launch {
                if (isIntentionalStop) {
                    if (_state.value is TextToSpeechState.Speaking) {
                        _state.value = TextToSpeechState.Idle
                    }
                } else {
                    AppLogger.w(TAG, "TTS onError: genuine playback error (code=$errorCode)")
                    _state.value = TextToSpeechState.Error("Speech error ($errorCode)")
                }
                dismissIslandIfSpeaking()
            }
        }
    }

    private companion object {
        const val TAG = "TextToSpeechManager"
    }
}
