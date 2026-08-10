package com.buddy.aios.core.domain.usecase

import com.buddy.aios.core.domain.entity.Conversation
import com.buddy.aios.core.domain.repository.IConversationRepository
import com.buddy.aios.core.domain.result.Result
import javax.inject.Inject

/**
 * Use case: Create a new conversation with a given persona.
 */
class CreateConversationUseCase @Inject constructor(
    private val conversationRepository: IConversationRepository,
) {
    suspend operator fun invoke(
        personaId: String,
        title: String = "New Chat",
    ): Result<String> {
        require(personaId.isNotBlank()) { "personaId must not be blank" }
        return conversationRepository.createConversation(personaId, title)
    }
}
