package com.buddy.aios.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.buddy.aios.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("""
        SELECT * FROM messages 
        WHERE conversation_id = :conversationId 
        ORDER BY timestamp ASC 
        LIMIT :limit
    """)
    suspend fun getRecentMessages(conversationId: String, limit: Int = 50): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteAllInConversation(conversationId: String)

    @Query("SELECT * FROM messages WHERE is_memory_anchor = 1 AND conversation_id = :conversationId")
    suspend fun getMemoryAnchors(conversationId: String): List<MessageEntity>

    @Query("UPDATE messages SET is_memory_anchor = 1 WHERE id = :messageId")
    suspend fun markAsMemoryAnchor(messageId: String)
}
