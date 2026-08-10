package com.buddy.aios.core.domain.repository

import com.buddy.aios.core.domain.entity.Conversation
import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for conversation and message operations.
 * Concrete implementation lives in :core:data — never referenced from domain.
 */
interface IConversationRepository {

    /** Observe all non-archived conversations, ordered by updatedAt desc. */
    fun observeConversations(): Flow<List<Conversation>>

    /** Observe all messages within a conversation, ordered by timestamp asc. */
    fun observeMessages(conversationId: String): Flow<List<Message>>

    /** Create a new conversation and return its ID. */
    suspend fun createConversation(personaId: String, title: String): Result<String>

    /** Send a user message and stream the AI reply back. */
    fun sendMessage(conversationId: String, content: String): Flow<Result<Message>>

    /** Archive a conversation (soft delete). */
    suspend fun archiveConversation(conversationId: String): Result<Unit>

    /** Delete a conversation and all its messages permanently. */
    suspend fun deleteConversation(conversationId: String): Result<Unit>

    /** Sync a conversation to the cloud (no-op if user is LOCAL_ONLY). */
    suspend fun syncConversation(conversationId: String): Result<Unit>

    /** Fetch full conversation detail by ID. */
    suspend fun getConversation(conversationId: String): Result<Conversation>
}
