package com.buddy.aios.core.domain.repository

import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for long-term memory operations.
 */
interface IMemoryRepository {

    /** Observe all memories for the current user, ordered by importance desc. */
    fun observeMemories(): Flow<List<Memory>>

    /** Search memories by text query. */
    suspend fun searchMemories(query: String): Result<List<Memory>>

    /** Store a new memory extracted from a conversation. */
    suspend fun saveMemory(memory: Memory): Result<Unit>

    /** Update an existing memory's importance or summary. */
    suspend fun updateMemory(memory: Memory): Result<Unit>

    /** Delete a specific memory. */
    suspend fun deleteMemory(memoryId: String): Result<Unit>

    /** Prune expired or low-importance memories. Called by [MemoryDecayWorker]. */
    suspend fun pruneExpiredMemories(
        expirationCutoff: Long = System.currentTimeMillis(),
        minImportanceThreshold: Float = 0.1f,
    ): Result<Int>

    /** Mark a memory as accessed (updates lastAccessedAt). */
    suspend fun touchMemory(memoryId: String): Result<Unit>
}
