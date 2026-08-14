package com.buddy.aios.feature.chat.voice

import com.buddy.aios.core.ai.voice.VoiceCommand
import com.buddy.aios.core.ai.voice.VoiceCommandParser
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VoiceSessionState {
    data object Idle : VoiceSessionState
    data object Listening : VoiceSessionState
    data class Processing(val text: String) : VoiceSessionState
    data object Thinking : VoiceSessionState
    data class Speaking(val text: String) : VoiceSessionState
    data object WaitingForUser : VoiceSessionState
    data class Error(val message: String) : VoiceSessionState
}

/**
 * Continuous Voice Session Manager.
 * Orchestrates multi-turn continuous voice interaction without requiring the user
 * to repeatedly press the microphone button.
 */
@Singleton
class VoiceConversationSession @Inject constructor(
    private val voiceInputManager: VoiceInputManager,
    private val ttsManager: TextToSpeechManager,
    private val voiceCommandParser: VoiceCommandParser,
    private val buddyModeRepository: IBuddyModeRepository,
    private val morningWishEngine: com.buddy.aios.core.domain.repository.IMorningWishEngine,
) {
    companion object {
        private const val TAG = "VoiceConversationSession"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _sessionState = MutableStateFlow<VoiceSessionState>(VoiceSessionState.Idle)
    val sessionState: StateFlow<VoiceSessionState> = _sessionState.asStateFlow()

    private val _isContinuousModeActive = MutableStateFlow(false)
    val isContinuousModeActive: StateFlow<Boolean> = _isContinuousModeActive.asStateFlow()

    private var onSpeechResultListener: ((String) -> Unit)? = null
    private var onCommandDetectedListener: ((VoiceCommand) -> Unit)? = null

    init {
        // 1. Observe VoiceInputManager State
        scope.launch {
            voiceInputManager.state.collect { inputState ->
                when (inputState) {
                    is VoiceInputState.Listening -> {
                        if (_isContinuousModeActive.value) {
                            _sessionState.value = VoiceSessionState.Listening
                        }
                    }
                    is VoiceInputState.FinalResult -> {
                        val text = inputState.text
                        AppLogger.d(TAG, "Voice input received: $text")

                        val command = voiceCommandParser.parse(text)
                        if (command != null) {
                            handleVoiceCommand(command)
                        } else {
                            _sessionState.value = VoiceSessionState.Processing(text)
                            onSpeechResultListener?.invoke(text)
                        }
                    }
                    is VoiceInputState.Error -> {
                        if (_isContinuousModeActive.value) {
                            AppLogger.w(TAG, "Voice input error in continuous mode: ${inputState.message}")
                            _sessionState.value = VoiceSessionState.WaitingForUser
                            delay(1000)
                            if (_isContinuousModeActive.value && ttsManager.state.value !is TextToSpeechState.Speaking) {
                                voiceInputManager.startListening()
                            }
                        } else {
                            _sessionState.value = VoiceSessionState.Error(inputState.message)
                        }
                    }
                    else -> {}
                }
            }
        }

        // 2. Observe TextToSpeechManager State (AUTO RETURN TO LISTENING WHEN TTS FINISHES)
        scope.launch {
            ttsManager.state.collect { ttsState ->
                when (ttsState) {
                    is TextToSpeechState.Speaking -> {
                        _sessionState.value = VoiceSessionState.Speaking(ttsState.text)
                    }
                    is TextToSpeechState.Idle -> {
                        if (_isContinuousModeActive.value && _sessionState.value is VoiceSessionState.Speaking) {
                            AppLogger.d(TAG, "TTS finished speaking -> Auto-returning to LISTENING in continuous mode")
                            _sessionState.value = VoiceSessionState.WaitingForUser
                            delay(500) // Brief natural pause after TTS
                            if (_isContinuousModeActive.value) {
                                voiceInputManager.startListening()
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun startContinuousSession(onSpeechResult: (String) -> Unit, onCommandDetected: (VoiceCommand) -> Unit) {
        onSpeechResultListener = onSpeechResult
        onCommandDetectedListener = onCommandDetected
        _isContinuousModeActive.value = true
        AppLogger.d(TAG, "Started Continuous Voice Session")
        voiceInputManager.startListening()
    }

    fun stopContinuousSession() {
        _isContinuousModeActive.value = false
        voiceInputManager.stopListening()
        ttsManager.stop()
        _sessionState.value = VoiceSessionState.Idle
        AppLogger.d(TAG, "Stopped Continuous Voice Session")
    }

    fun toggleContinuousSession(onSpeechResult: (String) -> Unit, onCommandDetected: (VoiceCommand) -> Unit) {
        if (_isContinuousModeActive.value) {
            stopContinuousSession()
        } else {
            startContinuousSession(onSpeechResult, onCommandDetected)
        }
    }

    fun notifyThinking() {
        if (_isContinuousModeActive.value) {
            _sessionState.value = VoiceSessionState.Thinking
        }
    }

    fun speak(text: String) {
        ttsManager.speak(text)
    }

    private fun handleVoiceCommand(command: VoiceCommand) {
        AppLogger.d(TAG, "Handling detected VoiceCommand: $command")
        onCommandDetectedListener?.invoke(command)

        when (command) {
            is VoiceCommand.StopListening -> {
                speak("Okay, stopping continuous listening.")
                stopContinuousSession()
            }
            is VoiceCommand.SetVoiceMode -> {
                if (command.enabled) {
                    speak("Continuous voice mode is enabled.")
                } else {
                    speak("Voice mode turned off.")
                    stopContinuousSession()
                }
            }
            is VoiceCommand.SetBuddyModeCommand -> {
                scope.launch {
                    buddyModeRepository.setBuddyMode(command.mode)
                    speak("${command.mode.name.lowercase().replaceFirstChar { it.uppercase() }} mode is on.")
                }
            }
            is VoiceCommand.MorningWishCommand -> {
                scope.launch {
                    morningWishEngine.triggerMorningWish(isManualTrigger = true)
                }
            }
            is VoiceCommand.AcknowledgeMorningWishCommand -> {
                scope.launch {
                    if (morningWishEngine.isWaitingForAcknowledgement()) {
                        morningWishEngine.acknowledgeMorningWish(source = "voice")
                    }
                }
            }
            else -> {}
        }
    }
}
