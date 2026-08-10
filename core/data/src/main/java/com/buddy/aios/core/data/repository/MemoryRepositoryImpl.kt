package com.buddy.aios.core.data.repository

import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.data.mapper.toDomain
import com.buddy.aios.core.data.mapper.toEntity
import com.buddy.aios.core.database.dao.MemoryDao
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.result.AppError
import com.buddy.aios.core.domain.result.Result
import com.buddy.aios.core.security.EncryptionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepositoryImpl @Inject constructor(
    private val memoryDao: MemoryDao,
    private val encryptionService: EncryptionService,
    private val dispatchers: DispatcherProvider,
) : IMemoryRepository {

    override fun observeMemories(): Flow<List<Memory>> {
        return memoryDao.observeAllMemories()
            .map { list -> list.map { it.toDomain(encryptionService) } }
            .flowOn(dispatchers.io)
    }

    override suspend fun searchMemories(query: String): Result<List<Memory>> {
        return withContext(dispatchers.io) {
            try {
                val entities = memoryDao.searchMemories(query)
                val memories = entities.map { it.toDomain(encryptionService) }
                Result.Success(memories)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun saveMemory(memory: Memory): Result<Unit> {
        return withContext(dispatchers.io) {
            try {
                memoryDao.insert(memory.toEntity(encryptionService))
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun updateMemory(memory: Memory): Result<Unit> {
        return withContext(dispatchers.io) {
            try {
                memoryDao.update(memory.toEntity(encryptionService))
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun deleteMemory(memoryId: String): Result<Unit> {
        return withContext(dispatchers.io) {
            try {
                memoryDao.delete(memoryId)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun pruneExpiredMemories(
        expirationCutoff: Long,
        minImportanceThreshold: Float,
    ): Result<Int> {
        return withContext(dispatchers.io) {
            try {
                val prunedCount = memoryDao.pruneExpired(expirationCutoff, minImportanceThreshold)
                Result.Success(prunedCount)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun touchMemory(memoryId: String): Result<Unit> {
        return withContext(dispatchers.io) {
            try {
                memoryDao.touchMemory(memoryId, System.currentTimeMillis())
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }
}
