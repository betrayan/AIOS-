package com.buddy.aios.feature.chat.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.aios.core.ai.agent.AgentOrchestrator
import com.buddy.aios.core.ai.response.ResponseComposer
import com.buddy.aios.core.domain.agent.AgentStatus
import com.buddy.aios.core.domain.entity.BuddyCapability
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.entity.getCapabilities
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IConversationRepository
import com.buddy.aios.core.domain.repository.IUserRepository
import com.buddy.aios.core.domain.result.AIErrorType
import com.buddy.aios.core.domain.result.AppError
import com.buddy.aios.core.domain.result.Result
import com.buddy.aios.core.domain.usecase.ObserveMessagesUseCase
import com.buddy.aios.core.domain.usecase.SendMessageUseCase
import com.buddy.aios.core.ui.island.AIOSIslandState
import com.buddy.aios.core.ui.island.AIOSIslandStateManager
import com.buddy.aios.feature.chat.voice.TextToSpeechManager
import com.buddy.aios.feature.chat.voice.TextToSpeechState
import com.buddy.aios.feature.chat.voice.VoiceInputManager
import com.buddy.aios.feature.chat.voice.VoiceInputState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val conversationRepository: IConversationRepository,
    private val buddyModeRepository: IBuddyModeRepository,
    private val userRepository: IUserRepository,
    private val responseComposer: ResponseComposer,
    private val islandStateManager: AIOSIslandStateManager,
    val agentOrchestrator: AgentOrchestrator,
    val voiceInputManager: VoiceInputManager,
    val ttsManager: TextToSpeechManager,
    private val voiceCommandParser: com.buddy.aios.core.ai.voice.VoiceCommandParser,
    val voiceSession: com.buddy.aios.feature.chat.voice.VoiceConversationSession,
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _streamingPartialText = MutableStateFlow<String?>(null)

    val isContinuousVoiceActive: StateFlow<Boolean> = voiceSession.isContinuousModeActive

    val voiceInputState: StateFlow<VoiceInputState> = voiceInputManager.state
    val ttsState: StateFlow<TextToSpeechState> = ttsManager.state
    val agentStatus: StateFlow<AgentStatus> = agentOrchestrator.agentStatus

    val currentCapabilities: StateFlow<BuddyCapability> = buddyModeRepository.observeCapabilities()
        .catch { emit(BuddyMode.ACTIVE.getCapabilities()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BuddyMode.ACTIVE.getCapabilities(),
        )

    init {
        observeMessagesUseCase(conversationId)
            .onEach { messages ->
                _uiState.value = ChatUiState.Active(
                    messages = messages,
                    conversationTitle = "AIOS",
                    streamingPartialText = _streamingPartialText.value,
                )
            }
            .catch { e ->
                _uiState.value = ChatUiState.Error(
                    message = "Cloud AI couldn't respond.",
                    secondaryMessage = e.message ?: "Please check your AI connection and try again."
                )
            }
            .launchIn(viewModelScope)

        // Observe Agent Status and mirror into Dynamic Island (voice session active only)
        agentOrchestrator.agentStatus
            .onEach { status ->
                if (voiceSession.sessionMode.value == com.buddy.aios.feature.chat.voice.VoiceSessionMode.OFF) return@onEach
                when (status) {
                    AgentStatus.UNDERSTANDING -> islandStateManager.show(AIOSIslandState.THINKING, "Understanding...", autoDismissMs = 0L)
                    AgentStatus.PLANNING      -> islandStateManager.show(AIOSIslandState.THINKING, "Planning...", autoDismissMs = 0L)
                    AgentStatus.EXECUTING     -> islandStateManager.show(AIOSIslandState.TOOL_EXECUTION, "Working...", autoDismissMs = 0L)
                    AgentStatus.VERIFYING     -> islandStateManager.show(AIOSIslandState.TOOL_EXECUTION, "Checking...", autoDismissMs = 0L)
                    AgentStatus.WAITING_CONFIRMATION -> islandStateManager.show(AIOSIslandState.ERROR, "Confirmation needed", autoDismissMs = 0L)
                    AgentStatus.COMPLETED     -> islandStateManager.show(AIOSIslandState.TASK_CREATED, "Done", autoDismissMs = 2500L)
                    AgentStatus.FAILED        -> islandStateManager.show(AIOSIslandState.ERROR, "Couldn't complete", autoDismissMs = 3000L)
                    AgentStatus.CANCELLED     -> islandStateManager.dismiss()
                    AgentStatus.IDLE          -> { /* preserve current island or let auto-dismiss handle */ }
                }
            }
            .launchIn(viewModelScope)

        // Observe voice session state and mode into Dynamic Island
        viewModelScope.launch {
            voiceSession.sessionState.collect { sessionState ->
                val mode = voiceSession.sessionMode.value
                if (mode == com.buddy.aios.feature.chat.voice.VoiceSessionMode.OFF && sessionState is com.buddy.aios.feature.chat.voice.VoiceSessionState.Idle) {
                    return@collect
                }
                when (sessionState) {
                    is com.buddy.aios.feature.chat.voice.VoiceSessionState.Listening -> {
                        val state = if (mode == com.buddy.aios.feature.chat.voice.VoiceSessionMode.CONTINUOUS) {
                            AIOSIslandState.CONTINUOUS
                        } else {
                            AIOSIslandState.LISTENING
                        }
                        val msg = if (mode == com.buddy.aios.feature.chat.voice.VoiceSessionMode.CONTINUOUS) {
                            "● Conversation Mode"
                        } else {
                            "Listening..."
                        }
                        islandStateManager.show(state = state, message = msg, autoDismissMs = 0L)
                    }
                    is com.buddy.aios.feature.chat.voice.VoiceSessionState.Thinking -> {
                        if (mode != com.buddy.aios.feature.chat.voice.VoiceSessionMode.OFF) {
                            islandStateManager.show(state = AIOSIslandState.THINKING, message = "✦ Thinking...", autoDismissMs = 0L)
                        }
                    }
                    is com.buddy.aios.feature.chat.voice.VoiceSessionState.Speaking -> {
                        if (mode != com.buddy.aios.feature.chat.voice.VoiceSessionMode.OFF) {
                            islandStateManager.show(state = AIOSIslandState.SPEAKING, message = "🔊 Speaking...", autoDismissMs = 0L)
                        }
                    }
                    is com.buddy.aios.feature.chat.voice.VoiceSessionState.Processing -> {
                        if (mode != com.buddy.aios.feature.chat.voice.VoiceSessionMode.OFF) {
                            islandStateManager.show(state = AIOSIslandState.THINKING, message = "Processing...", autoDismissMs = 0L)
                        }
                        _inputText.value = sessionState.text
                        onSendMessage()
                    }
                    is com.buddy.aios.feature.chat.voice.VoiceSessionState.Idle -> {
                        val current = islandStateManager.displayState.value
                        if (current.state == AIOSIslandState.LISTENING ||
                            current.state == AIOSIslandState.SPEAKING ||
                            current.state == AIOSIslandState.CONTINUOUS ||
                            current.state == AIOSIslandState.THINKING
                        ) {
                            islandStateManager.show(state = AIOSIslandState.STOPPING, message = "Stopping...", autoDismissMs = 600L)
                        }
                    }
                    is com.buddy.aios.feature.chat.voice.VoiceSessionState.Error -> {
                        _uiState.value = ChatUiState.Error(
                            message = "Voice input issue",
                            secondaryMessage = sessionState.message
                        )
                        islandStateManager.show(
                            state = AIOSIslandState.ERROR,
                            message = "Voice issue",
                            autoDismissMs = 2500L,
                        )
                    }
                    else -> {}
                }
            }
        }

        // Observe partial voice recognizer results for UI text box preview
        voiceInputManager.state
            .onEach { state ->
                if (state is VoiceInputState.PartialResult) {
                    _inputText.value = state.text
                }
            }
            .launchIn(viewModelScope)
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun toggleVoiceInput() {
        ttsManager.stop()
        voiceSession.toggleVoiceSession(
            onSpeechResult = { text ->
                _inputText.value = text
                onSendMessage()
            },
            onCommandDetected = { command ->
                com.buddy.aios.core.common.logging.AppLogger.d(TAG, "Command detected: $command")
            }
        )
    }

    fun onSendMessage() {
        val content = _inputText.value.trim()
        if (content.isBlank() || _isStreaming.value) return

        // Stop TTS only when the message originates from a text-typed user action, not from
        // the voice pipeline. For voice sessions, VoiceConversationSession controls TTS lifecycle.
        // Unconditionally stopping here was killing TTS mid-sentence on rapid interactions.
        val isVoiceSessionActive = voiceSession.sessionMode.value != com.buddy.aios.feature.chat.voice.VoiceSessionMode.OFF
        if (!isVoiceSessionActive) {
            ttsManager.stop()
        }

        voiceSession.notifyThinking()
        _inputText.value = ""
        _isStreaming.value = true
        _streamingPartialText.value = ""
        _uiState.value = ChatUiState.Thinking

        // Handle Confirmation responses ("Yes" / "Confirm" vs "No" / "Cancel")
        if (agentOrchestrator.agentStatus.value == AgentStatus.WAITING_CONFIRMATION) {
            val confirmed = content.equals("yes", ignoreCase = true) || content.equals("confirm", ignoreCase = true)
            viewModelScope.launch {
                val result = agentOrchestrator.confirmGoal(confirmed)
                _isStreaming.value = false
                deliverCompleteResponse(userMessage = content, fullResponse = result.summary, toolLabel = if (result.success) "Confirmed" else "Cancelled", toolResult = if (result.success) "success" else "failure", toolType = null)
            }
            return
        }

        // Handle Cancellation ("Stop" / "Cancel")
        if (content.equals("stop", ignoreCase = true) || content.equals("cancel", ignoreCase = true)) {
            agentOrchestrator.cancelGoal()
            _isStreaming.value = false
            islandStateManager.show(AIOSIslandState.AIOS_MESSAGE, "Task stopped", autoDismissMs = 2000L)
            return
        }

        sendMessageUseCase(conversationId, content)
            .onEach { result ->
                when (result) {
                    is Result.Success -> {
                        val msg = result.value
                        _streamingPartialText.value = msg.content
                        val currentState = _uiState.value
                        if (currentState is ChatUiState.Active) {
                            _uiState.value = currentState.copy(streamingPartialText = msg.content)
                        } else {
                            _uiState.value = ChatUiState.Active(
                                messages = emptyList(),
                                conversationTitle = "AIOS",
                                streamingPartialText = msg.content
                            )
                        }

                        // On complete response: trigger Agent Brain check and deliver voice/island
                        if (msg.isComplete && msg.content.isNotBlank()) {
                            viewModelScope.launch {
                                deliverCompleteResponse(
                                    userMessage = content,
                                    fullResponse = msg.content,
                                    toolLabel = msg.metadata["tool_label"],
                                    toolResult = msg.metadata["tool_result"],
                                    toolType = msg.metadata["tool_type"],
                                )
                            }
                        }
                    }
                    is Result.Error -> {
                        val (primaryMsg, secondaryMsg, is404) = when (val err = result.error) {
                            is AppError.AIError -> when (err.type) {
                                AIErrorType.MODEL_NOT_FOUND -> Triple(
                                    "AI model configuration needs an update.",
                                    "The requested AI model version is currently unavailable.",
                                    true
                                )
                                AIErrorType.AUTHENTICATION_ERROR -> Triple(
                                    "Cloud AI authentication failed.",
                                    "Please check your API key in settings and try again.",
                                    false
                                )
                                AIErrorType.CLOUD_QUOTA_EXCEEDED -> Triple(
                                    "Cloud AI quota exceeded.",
                                    "Rate limit reached. Please try again shortly.",
                                    false
                                )
                                else -> Triple(
                                    "Cloud AI couldn't respond.",
                                    "Please check your AI connection and try again.",
                                    false
                                )
                            }
                            else -> Triple(
                                "Cloud AI couldn't respond.",
                                "Please check your AI connection and try again.",
                                false
                            )
                        }
                        _uiState.value = ChatUiState.Error(
                            message = primaryMsg,
                            secondaryMessage = secondaryMsg,
                            isModelNotFound = is404
                        )
                        _isStreaming.value = false
                        _streamingPartialText.value = null
                        islandStateManager.show(
                            state = AIOSIslandState.ERROR,
                            message = "Couldn't respond",
                            autoDismissMs = 3000L,
                        )
                    }
                }
            }
            .onCompletion {
                _isStreaming.value = false
                _streamingPartialText.value = null
            }
            .launchIn(viewModelScope)
    }

    private suspend fun deliverCompleteResponse(
        userMessage: String,
        fullResponse: String,
        toolLabel: String?,
        toolResult: String?,
        toolType: String?,
    ) {
        val buddyMode = buddyModeRepository.getBuddyMode()
        val privacyLevel = (userRepository.getUserProfile() as? Result.Success)
            ?.value?.privacyLevel ?: PrivacyLevel.LOCAL_ONLY

        // ── Dynamic Island ─────────────────────────────────────────────────────
        when {
            toolLabel != null && toolResult == "success" -> {
                val islandState = when (toolType) {
                    "SaveMemory", "DeleteMemory" -> AIOSIslandState.MEMORY_SAVED
                    else -> AIOSIslandState.TASK_CREATED
                }
                islandStateManager.show(
                    state = islandState,
                    message = toolLabel,
                    autoDismissMs = 3000L,
                    actionLabel = "Open Chat",
                )
            }
            toolLabel != null && toolResult == "failure" -> {
                islandStateManager.show(
                    state = AIOSIslandState.ERROR,
                    message = "Couldn't complete that",
                    autoDismissMs = 3000L,
                )
            }
            else -> {
                islandStateManager.dismiss()
            }
        }

        // ── Voice (TTS) — active voice session only ─────────────────────────────
        if (voiceSession.sessionMode.value != com.buddy.aios.feature.chat.voice.VoiceSessionMode.OFF && currentCapabilities.value.allowVoiceInputOutput) {
            val composed = responseComposer.compose(
                userMessage = userMessage,
                fullResponse = fullResponse,
                toolLabel = toolLabel,
                buddyMode = buddyMode,
                privacyLevel = privacyLevel,
            )
            if (composed.voiceText.isNotBlank()) {
                islandStateManager.show(
                    state = AIOSIslandState.SPEAKING,
                    message = "🔊 Speaking...",
                    autoDismissMs = 0L,
                )
                ttsManager.speak(composed.voiceText)
            }
        }
    }

    fun onClearConversation(onCleared: () -> Unit) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversationId)
            onCleared()
        }
    }

    fun onRetry() {
        _uiState.value = ChatUiState.Loading
        islandStateManager.dismiss()
    }

    override fun onCleared() {
        super.onCleared()
        voiceSession.stopContinuousSession()
    }
}
