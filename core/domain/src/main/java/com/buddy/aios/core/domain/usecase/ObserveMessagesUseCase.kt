package com.buddy.aios.core.domain.usecase

import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.repository.IConversationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case: Observe messages for a given conversation.
 */
class ObserveMessagesUseCase @Inject constructor(
    private val conversationRepository: IConversationRepository,
) {
    operator fun invoke(conversationId: String): Flow<List<Message>> =
        conversationRepository.observeMessages(conversationId)
}
