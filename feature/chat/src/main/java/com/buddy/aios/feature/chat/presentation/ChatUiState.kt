package com.buddy.aios.feature.chat.presentation

import com.buddy.aios.core.domain.entity.Message

/**
 * Robust UI State hierarchy for Chat screen.
 */
sealed interface ChatUiState {
    data object Loading : ChatUiState
    data object Thinking : ChatUiState
    data class Active(
        val messages: List<Message>,
        val conversationTitle: String,
        val streamingPartialText: String? = null,
    ) : ChatUiState
    data class Error(
        val message: String,
        val secondaryMessage: String = "Please check your AI connection and try again.",
        val isModelNotFound: Boolean = false,
    ) : ChatUiState
}
