package com.buddy.aios.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.buddy.aios.core.database.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories ORDER BY importance DESC, last_accessed_at DESC")
    fun observeAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE summary_encrypted LIKE '%' || :query || '%' ORDER BY importance DESC")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE memories SET last_accessed_at = :timestamp WHERE id = :id")
    suspend fun touchMemory(id: String, timestamp: Long)

    @Query("DELETE FROM memories WHERE (expires_at IS NOT NULL AND expires_at <= :expirationCutoff) OR (importance < :minImportanceThreshold)")
    suspend fun pruneExpired(expirationCutoff: Long, minImportanceThreshold: Float): Int
}
