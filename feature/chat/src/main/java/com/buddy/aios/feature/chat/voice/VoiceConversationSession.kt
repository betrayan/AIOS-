package com.buddy.aios.feature.chat.voice

import com.buddy.aios.core.ai.voice.VoiceCommand
import com.buddy.aios.core.ai.voice.VoiceCommandParser
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceSessionMode {
    OFF,
    ONE_SHOT,
    CONTINUOUS,
}

sealed interface VoiceSessionState {
    data object Idle : VoiceSessionState
    data object Listening : VoiceSessionState
    data class Processing(val text: String) : VoiceSessionState
    data object Thinking : VoiceSessionState
    data class Speaking(val text: String) : VoiceSessionState
    data object WaitingForUser : VoiceSessionState
    data object Paused : VoiceSessionState
    data class Error(val message: String) : VoiceSessionState
}

/**
 * Production-Ready Voice Session Manager supporting default ONE_SHOT voice
 * and explicitly activated CONTINUOUS conversation mode.
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
        private const val MAX_CONTINUOUS_ERRORS = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _sessionMode = MutableStateFlow(VoiceSessionMode.OFF)
    val sessionMode: StateFlow<VoiceSessionMode> = _sessionMode.asStateFlow()

    private val _sessionState = MutableStateFlow<VoiceSessionState>(VoiceSessionState.Idle)
    val sessionState: StateFlow<VoiceSessionState> = _sessionState.asStateFlow()

    val isContinuousModeActive: StateFlow<Boolean> = _sessionMode
        .map { it == VoiceSessionMode.CONTINUOUS }
        .stateIn(
            scope = scope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = false
        )

    private var onSpeechResultListener: ((String) -> Unit)? = null
    private var onCommandDetectedListener: ((VoiceCommand) -> Unit)? = null
    private var autoListenJob: Job? = null
    private var errorCount = 0
    private var sessionToken = 0L

    init {
        // 1. Observe VoiceInputManager State
        scope.launch {
            voiceInputManager.state.collect { inputState ->
                when (inputState) {
                    is VoiceInputState.Listening -> {
                        if (_sessionMode.value != VoiceSessionMode.OFF && _sessionState.value != VoiceSessionState.Paused) {
                            _sessionState.value = VoiceSessionState.Listening
                        }
                    }
                    is VoiceInputState.FinalResult -> {
                        errorCount = 0
                        val text = inputState.text
                        AppLogger.d(TAG, "Voice input received (mode=${_sessionMode.value}): $text")

                        val command = voiceCommandParser.parse(text)
                        if (command != null) {
                            handleVoiceCommand(command)
                        } else {
                            _sessionState.value = VoiceSessionState.Processing(text)
                            onSpeechResultListener?.invoke(text)
                        }
                    }
                    is VoiceInputState.SpeechTimeout -> {
                        if (_sessionState.value is VoiceSessionState.Processing || _sessionState.value is VoiceSessionState.Speaking) {
                            AppLogger.d(TAG, "Ignoring trailing SpeechTimeout while processing/speaking speech result")
                            return@collect
                        }
                        when (_sessionMode.value) {
                            VoiceSessionMode.CONTINUOUS -> {
                                if (_sessionState.value != VoiceSessionState.Paused) {
                                    if (ttsManager.state.value is TextToSpeechState.Speaking) {
                                        AppLogger.d(TAG, "SpeechTimeout while TTS speaking -> Deferring auto-restart until TTS completes")
                                    } else {
                                        AppLogger.d(TAG, "Silence timeout in CONTINUOUS mode -> Quietly auto-restarting listening")
                                        _sessionState.value = VoiceSessionState.WaitingForUser
                                        scheduleAutoRestartListening(delayMs = 300L)
                                    }
                                }
                            }
                            VoiceSessionMode.ONE_SHOT -> {
                                AppLogger.d(TAG, "Silence timeout in ONE_SHOT mode -> Finishing voice session")
                                stopSession()
                            }
                            VoiceSessionMode.OFF -> {}
                        }
                    }
                    is VoiceInputState.Error -> {
                        if (_sessionState.value is VoiceSessionState.Processing || _sessionState.value is VoiceSessionState.Speaking) {
                            AppLogger.d(TAG, "Ignoring trailing SpeechRecognizer error (${inputState.message}) while processing/speaking speech result")
                            return@collect
                        }
                        when (_sessionMode.value) {
                            VoiceSessionMode.CONTINUOUS -> {
                                if (_sessionState.value != VoiceSessionState.Paused) {
                                    if (ttsManager.state.value is TextToSpeechState.Speaking) {
                                        AppLogger.d(TAG, "Voice input error while TTS speaking (${inputState.message}) -> Deferring auto-restart until TTS completes")
                                    } else {
                                        errorCount++
                                        if (errorCount <= MAX_CONTINUOUS_ERRORS) {
                                            AppLogger.w(TAG, "Voice input error in CONTINUOUS mode ($errorCount/$MAX_CONTINUOUS_ERRORS): ${inputState.message}")
                                            _sessionState.value = VoiceSessionState.WaitingForUser
                                            scheduleAutoRestartListening(delayMs = 600L)
                                        } else {
                                            AppLogger.w(TAG, "Max continuous voice errors reached ($errorCount). Stopping continuous mode.")
                                            stopSession()
                                        }
                                    }
                                }
                            }
                            VoiceSessionMode.ONE_SHOT -> {
                                AppLogger.w(TAG, "Voice input error in ONE_SHOT mode: ${inputState.message}")
                                stopSession()
                            }
                            VoiceSessionMode.OFF -> {}
                        }
                    }
                    else -> {}
                }
            }
        }

        // 2. Observe TextToSpeechManager State (BRANCHING LOGIC ON TTS COMPLETE)
        scope.launch {
            ttsManager.state.collect { ttsState ->
                // Ignore background TTS (e.g. Reminders or Morning Wish) when voice session is OFF
                if (_sessionMode.value == VoiceSessionMode.OFF) return@collect

                when (ttsState) {
                    is TextToSpeechState.Speaking -> {
                        AppLogger.d(TAG, "TTS: START")
                        _sessionState.value = VoiceSessionState.Speaking(ttsState.text)
                    }
                    is TextToSpeechState.Idle -> {
                        AppLogger.d(TAG, "TTS: COMPLETE")
                        when (_sessionMode.value) {
                            VoiceSessionMode.CONTINUOUS -> {
                                if (_sessionState.value != VoiceSessionState.Paused) {
                                    AppLogger.d(TAG, "TTS finished speaking in CONTINUOUS mode -> Auto-returning to LISTENING")
                                    _sessionState.value = VoiceSessionState.WaitingForUser
                                    scheduleAutoRestartListening(delayMs = 300L)
                                }
                            }
                            VoiceSessionMode.ONE_SHOT -> {
                                if (_sessionState.value is VoiceSessionState.Speaking || _sessionState.value is VoiceSessionState.Processing) {
                                    AppLogger.d(TAG, "TTS finished speaking in ONE_SHOT mode -> Finishing voice session")
                                    stopSession()
                                }
                            }
                            VoiceSessionMode.OFF -> {}
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun startOneShotSession(
        onSpeechResult: ((String) -> Unit)? = null,
        onCommandDetected: ((VoiceCommand) -> Unit)? = null
    ) {
        if (onSpeechResult != null) onSpeechResultListener = onSpeechResult
        if (onCommandDetected != null) onCommandDetectedListener = onCommandDetected

        val previousMode = _sessionMode.value
        if (previousMode == VoiceSessionMode.ONE_SHOT && _sessionState.value is VoiceSessionState.Listening) {
            AppLogger.d(TAG, "One-shot session already active and listening")
            return
        }

        autoListenJob?.cancel()
        sessionToken++
        errorCount = 0
        _sessionMode.value = VoiceSessionMode.ONE_SHOT
        _sessionState.value = VoiceSessionState.Listening

        if (previousMode == VoiceSessionMode.OFF) {
            voiceInputManager.playMicSound(isOn = true)
            AppLogger.d(TAG, "VoiceSession: OFF → ONE_SHOT")
        }

        voiceInputManager.startListening()
    }

    fun startContinuousSession(
        onSpeechResult: ((String) -> Unit)? = null,
        onCommandDetected: ((VoiceCommand) -> Unit)? = null
    ) {
        if (onSpeechResult != null) onSpeechResultListener = onSpeechResult
        if (onCommandDetected != null) onCommandDetectedListener = onCommandDetected

        val previousMode = _sessionMode.value
        if (previousMode == VoiceSessionMode.CONTINUOUS && _sessionState.value is VoiceSessionState.Listening) {
            AppLogger.d(TAG, "Continuous session already active and listening")
            return
        }

        autoListenJob?.cancel()
        sessionToken++
        errorCount = 0
        _sessionMode.value = VoiceSessionMode.CONTINUOUS
        _sessionState.value = VoiceSessionState.Listening

        if (previousMode == VoiceSessionMode.OFF) {
            voiceInputManager.playMicSound(isOn = true)
            AppLogger.d(TAG, "VoiceSession: OFF → CONTINUOUS")
        } else {
            AppLogger.d(TAG, "VoiceSession: ${previousMode.name} → CONTINUOUS")
        }

        voiceInputManager.startListening()
    }

    fun stopSession() {
        autoListenJob?.cancel()
        autoListenJob = null
        sessionToken++

        val previousMode = _sessionMode.value
        if (previousMode == VoiceSessionMode.OFF) {
            AppLogger.d(TAG, "VoiceSession: Already OFF")
            return
        }

        _sessionMode.value = VoiceSessionMode.OFF
        errorCount = 0

        voiceInputManager.stopListening()
        voiceInputManager.cancel()
        ttsManager.stop()
        _sessionState.value = VoiceSessionState.Idle

        voiceInputManager.playMicSound(isOn = false)
        AppLogger.d(TAG, "VoiceSession: ${previousMode.name} → OFF")
    }

    fun stopContinuousSession() {
        stopSession()
    }

    fun pauseSession() {
        autoListenJob?.cancel()
        autoListenJob = null
        if (_sessionMode.value != VoiceSessionMode.OFF) {
            _sessionState.value = VoiceSessionState.Paused
            voiceInputManager.stopListening()
            AppLogger.d(TAG, "Paused Voice Session")
        }
    }

    fun resumeSession() {
        if (_sessionMode.value != VoiceSessionMode.OFF && _sessionState.value == VoiceSessionState.Paused) {
            _sessionState.value = VoiceSessionState.Listening
            AppLogger.d(TAG, "Resumed Voice Session from Pause")
            voiceInputManager.startListening()
        }
    }

    fun toggleVoiceSession(
        onSpeechResult: ((String) -> Unit)? = null,
        onCommandDetected: ((VoiceCommand) -> Unit)? = null
    ) {
        if (_sessionMode.value != VoiceSessionMode.OFF) {
            stopSession()
        } else {
            startOneShotSession(onSpeechResult, onCommandDetected)
        }
    }

    fun toggleContinuousSession(
        onSpeechResult: ((String) -> Unit)? = null,
        onCommandDetected: ((VoiceCommand) -> Unit)? = null
    ) {
        if (_sessionMode.value == VoiceSessionMode.CONTINUOUS) {
            stopSession()
        } else {
            startContinuousSession(onSpeechResult, onCommandDetected)
        }
    }

    fun notifyThinking() {
        if (_sessionMode.value != VoiceSessionMode.OFF && _sessionState.value != VoiceSessionState.Paused) {
            _sessionState.value = VoiceSessionState.Thinking
        }
    }

    fun speak(text: String) {
        ttsManager.speak(text)
    }

    private fun scheduleAutoRestartListening(delayMs: Long) {
        autoListenJob?.cancel()
        val currentToken = sessionToken
        if (_sessionMode.value != VoiceSessionMode.CONTINUOUS || _sessionState.value == VoiceSessionState.Paused) return

        autoListenJob = scope.launch {
            delay(delayMs)
            if (sessionToken == currentToken &&
                _sessionMode.value == VoiceSessionMode.CONTINUOUS &&
                _sessionState.value != VoiceSessionState.Paused &&
                ttsManager.state.value !is TextToSpeechState.Speaking
            ) {
                AppLogger.d(TAG, "Auto-restarting listening after delay=${delayMs}ms in CONTINUOUS mode")
                _sessionState.value = VoiceSessionState.Listening
                voiceInputManager.startListening()
            } else if (ttsManager.state.value is TextToSpeechState.Speaking) {
                AppLogger.d(TAG, "Auto-restart listening deferred: TTS is currently speaking")
            }
        }
    }

    private fun handleVoiceCommand(command: VoiceCommand) {
        AppLogger.d(TAG, "Handling detected VoiceCommand: $command")
        onCommandDetectedListener?.invoke(command)

        when (command) {
            is VoiceCommand.StopListening -> {
                speak("Okay, stopping voice mode.")
                stopSession()
            }
            is VoiceCommand.PauseListening -> {
                speak("Voice listening paused.")
                pauseSession()
            }
            is VoiceCommand.ResumeListening -> {
                speak("Resuming voice listening.")
                resumeSession()
            }
            is VoiceCommand.SetVoiceMode -> {
                if (command.enabled) {
                    startContinuousSession()
                    speak("Continuous conversation mode enabled.")
                } else {
                    speak("Continuous mode turned off.")
                    stopSession()
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

