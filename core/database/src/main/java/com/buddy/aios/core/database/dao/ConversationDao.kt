package com.buddy.aios.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.buddy.aios.core.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE is_archived = 0 ORDER BY updated_at DESC")
    fun observeActiveConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET is_archived = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun archive(id: String, timestamp: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE conversations SET synced_at = :timestamp WHERE id = :id")
    suspend fun markSynced(id: String, timestamp: Long)

    @Query("UPDATE conversations SET message_count = message_count + 1, updated_at = :timestamp WHERE id = :id")
    suspend fun incrementMessageCount(id: String, timestamp: Long)
}
