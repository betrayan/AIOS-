package com.buddy.aios.core.domain.usecase

import com.buddy.aios.core.domain.entity.Conversation
import com.buddy.aios.core.domain.repository.IConversationRepository
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case: Observe the full list of conversations for the home screen.
 */
class ObserveConversationsUseCase @Inject constructor(
    private val conversationRepository: IConversationRepository,
) {
    operator fun invoke(): Flow<List<Conversation>> =
        conversationRepository.observeConversations()
}
