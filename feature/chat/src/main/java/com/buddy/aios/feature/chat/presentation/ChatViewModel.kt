package com.buddy.aios.feature.chat.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.aios.core.domain.entity.BuddyCapability
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.getCapabilities
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IConversationRepository
import com.buddy.aios.core.domain.result.AIErrorType
import com.buddy.aios.core.domain.result.AppError
import com.buddy.aios.core.domain.result.Result
import com.buddy.aios.core.domain.usecase.ObserveMessagesUseCase
import com.buddy.aios.core.domain.usecase.SendMessageUseCase
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
    val voiceInputManager: VoiceInputManager,
    val ttsManager: TextToSpeechManager,
) : ViewModel() {

    val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _streamingPartialText = MutableStateFlow<String?>(null)

    val voiceInputState: StateFlow<VoiceInputState> = voiceInputManager.state
    val ttsState: StateFlow<TextToSpeechState> = ttsManager.state

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
                    conversationTitle = "Buddy Chat",
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

        // Observe voice recognizer results
        voiceInputManager.state
            .onEach { state ->
                when (state) {
                    is VoiceInputState.PartialResult -> _inputText.value = state.text
                    is VoiceInputState.FinalResult -> {
                        _inputText.value = state.text
                        onSendMessage()
                    }
                    is VoiceInputState.Error -> {
                        _uiState.value = ChatUiState.Error(
                            message = "Voice input issue",
                            secondaryMessage = state.message
                        )
                    }
                    else -> {}
                }
            }
            .launchIn(viewModelScope)
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun toggleVoiceInput() {
        // Interruption rule: stop ongoing TTS if speaking
        ttsManager.stop()

        if (voiceInputState.value is VoiceInputState.Listening || voiceInputState.value is VoiceInputState.PartialResult) {
            voiceInputManager.stopListening()
        } else {
            voiceInputManager.startListening()
        }
    }

    fun onSendMessage() {
        val content = _inputText.value.trim()
        if (content.isBlank() || _isStreaming.value) return

        // Interruption rule: stop ongoing TTS when new message is sent
        ttsManager.stop()

        _inputText.value = ""
        _isStreaming.value = true
        _streamingPartialText.value = ""
        _uiState.value = ChatUiState.Thinking

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
                                conversationTitle = "Buddy Chat",
                                streamingPartialText = msg.content
                            )
                        }

                        // Speak AI response if voice output is allowed in current BuddyMode
                        if (msg.content.isNotBlank() && currentCapabilities.value.allowVoiceInputOutput) {
                            ttsManager.speak(msg.content)
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
                    }
                }
            }
            .onCompletion {
                _isStreaming.value = false
                _streamingPartialText.value = null
            }
            .launchIn(viewModelScope)
    }

    fun onClearConversation(onCleared: () -> Unit) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversationId)
            onCleared()
        }
    }

    fun onRetry() {
        _uiState.value = ChatUiState.Loading
    }

    override fun onCleared() {
        super.onCleared()
        voiceInputManager.stopListening()
        ttsManager.stop()
    }
}
