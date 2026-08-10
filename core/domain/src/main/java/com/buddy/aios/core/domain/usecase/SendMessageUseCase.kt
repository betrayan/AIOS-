package com.buddy.aios.core.domain.usecase

import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.repository.IConversationRepository
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case: Send a user message and receive the AI streaming response.
 *
 * Single responsibility: validates input, delegates to repository, returns stream.
 * All AI routing, context management, and persistence happen in the data layer.
 */
class SendMessageUseCase @Inject constructor(
    private val conversationRepository: IConversationRepository,
) {
    /**
     * @param conversationId The conversation to append the message to.
     * @param content The raw user message text (non-blank, trimmed).
     * @return A [Flow] of [Result<Message>] — emits partial assistant tokens as they arrive,
     *         then emits a final complete [Message] with [Result.Success].
     */
    operator fun invoke(
        conversationId: String,
        content: String,
    ): Flow<Result<Message>> {
        require(conversationId.isNotBlank()) { "conversationId must not be blank" }
        require(content.isNotBlank()) { "Message content must not be blank" }
        return conversationRepository.sendMessage(conversationId, content.trim())
    }
}
