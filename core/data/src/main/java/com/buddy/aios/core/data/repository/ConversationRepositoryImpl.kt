package com.buddy.aios.core.data.repository

import com.buddy.aios.core.ai.engine.AIEngine
import com.buddy.aios.core.ai.engine.AIPrompt
import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.data.mapper.toDomain
import com.buddy.aios.core.data.mapper.toEntity
import com.buddy.aios.core.database.dao.ConversationDao
import com.buddy.aios.core.database.dao.MessageDao
import com.buddy.aios.core.database.entity.ConversationEntity
import com.buddy.aios.core.domain.entity.Conversation
import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.entity.MessageRole
import com.buddy.aios.core.domain.repository.IConversationRepository
import com.buddy.aios.core.domain.result.AppError
import com.buddy.aios.core.domain.result.Result
import com.buddy.aios.core.security.EncryptionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val encryptionService: EncryptionService,
    private val aiEngine: AIEngine,
    private val dispatchers: DispatcherProvider,
) : IConversationRepository {

    override fun observeConversations(): Flow<List<Conversation>> {
        return conversationDao.observeActiveConversations()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override fun observeMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.observeMessages(conversationId)
            .map { list -> list.map { it.toDomain(encryptionService) } }
            .flowOn(dispatchers.io)
    }

    override suspend fun createConversation(personaId: String, title: String): Result<String> {
        return withContext(dispatchers.io) {
            try {
                val id = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val conversation = Conversation(
                    id = id,
                    title = title,
                    createdAt = now,
                    updatedAt = now,
                    personaId = personaId,
                )
                conversationDao.insert(conversation.toEntity())
                Result.Success(id)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override fun sendMessage(conversationId: String, content: String): Flow<Result<Message>> = flow {
        val now = System.currentTimeMillis()

        // 0. Pre-create conversation row if missing to satisfy SQLite FOREIGN KEY constraint
        withContext(dispatchers.io) {
            try {
                val existing = conversationDao.getById(conversationId)
                if (existing == null) {
                    AppLogger.d("ConversationRepository", "Auto-creating conversation $conversationId before inserting message")
                    val newConv = ConversationEntity(
                        id = conversationId,
                        title = "Buddy Chat",
                        createdAt = now,
                        updatedAt = now,
                        personaId = "default",
                    )
                    conversationDao.insert(newConv)
                }
            } catch (e: Exception) {
                AppLogger.e("ConversationRepository", "Failed to ensure conversation exists", e)
            }
        }

        val userMsgId = UUID.randomUUID().toString()
        val userMessage = Message(
            id = userMsgId,
            conversationId = conversationId,
            role = MessageRole.USER,
            content = content,
            timestamp = now,
        )

        // 1. Persist user message safely
        try {
            withContext(dispatchers.io) {
                messageDao.insert(userMessage.toEntity(encryptionService))
                conversationDao.incrementMessageCount(conversationId, now)
            }
        } catch (e: Exception) {
            AppLogger.e("ConversationRepository", "Failed to insert user message", e)
            emit(Result.Error(AppError.StorageError(e)))
            return@flow
        }

        // 2. Fetch history for context
        val history = try {
            withContext(dispatchers.io) {
                val recentEntities = messageDao.getRecentMessages(conversationId, 20)
                recentEntities.map { it.toDomain(encryptionService) }
            }
        } catch (e: Exception) {
            AppLogger.e("ConversationRepository", "Failed to fetch conversation history", e)
            emptyList()
        }

        // 3. Build AI Prompt
        val prompt = AIPrompt(
            systemInstruction = "You are Buddy, a warm, intelligent AI companion sitting right beside the user. Respond naturally and helpfully.",
            conversationHistory = history,
            userMessage = content,
            conversationId = conversationId,
        )

        // 4. Stream AI response
        val assistantMsgId = UUID.randomUUID().toString()
        val accumulatedText = StringBuilder()

        try {
            aiEngine.complete(prompt).collect { chunkResult ->
                when (chunkResult) {
                    is Result.Success -> {
                        val chunk = chunkResult.value
                        accumulatedText.append(chunk.text)
                        val currentAssistantMessage = Message(
                            id = assistantMsgId,
                            conversationId = conversationId,
                            role = MessageRole.ASSISTANT,
                            content = accumulatedText.toString(),
                            timestamp = System.currentTimeMillis(),
                        )
                        emit(Result.Success(currentAssistantMessage))

                        if (chunk.isComplete) {
                            // Persist final complete assistant message
                            try {
                                withContext(dispatchers.io) {
                                    messageDao.insert(currentAssistantMessage.toEntity(encryptionService))
                                    conversationDao.incrementMessageCount(conversationId, System.currentTimeMillis())
                                }
                            } catch (e: Exception) {
                                AppLogger.e("ConversationRepository", "Failed to insert assistant message", e)
                            }
                        }
                    }
                    is Result.Error -> {
                        emit(Result.Error(chunkResult.error))
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("ConversationRepository", "Unhandled exception during AI streaming completion", e)
            emit(Result.Error(AppError.UnknownError(e)))
        }
    }.flowOn(dispatchers.io)

    override suspend fun archiveConversation(conversationId: String): Result<Unit> {
        return withContext(dispatchers.io) {
            try {
                conversationDao.archive(conversationId, System.currentTimeMillis())
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun deleteConversation(conversationId: String): Result<Unit> {
        return withContext(dispatchers.io) {
            try {
                conversationDao.delete(conversationId)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun syncConversation(conversationId: String): Result<Unit> {
        return Result.Success(Unit)
    }

    override suspend fun getConversation(conversationId: String): Result<Conversation> {
        return withContext(dispatchers.io) {
            val entity = conversationDao.getById(conversationId)
            if (entity != null) {
                Result.Success(entity.toDomain())
            } else {
                Result.Error(AppError.StorageError(NoSuchElementException("Conversation not found")))
            }
        }
    }
}
