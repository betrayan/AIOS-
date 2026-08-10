package com.buddy.aios.core.data.repository

import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.data.mapper.toDomain
import com.buddy.aios.core.data.mapper.toEntity
import com.buddy.aios.core.database.dao.TaskDao
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.repository.ITaskRepository
import com.buddy.aios.core.domain.result.AppError
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val dispatchers: DispatcherProvider,
) : ITaskRepository {

    override fun observeActiveTasks(): Flow<List<Task>> {
        return taskDao.observeActiveTasks()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override fun observeCompletedTasks(): Flow<List<Task>> {
        return taskDao.observeCompletedTasks()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun getUpcomingTasks(fromTimestamp: Long): Result<List<Task>> {
        return withContext(dispatchers.io) {
            try {
                val entities = taskDao.getUpcomingTasks(fromTimestamp)
                Result.Success(entities.map { it.toDomain() })
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun getTaskById(taskId: String): Result<Task> {
        return withContext(dispatchers.io) {
            try {
                val entity = taskDao.getById(taskId)
                if (entity != null) {
                    Result.Success(entity.toDomain())
                } else {
                    Result.Error(AppError.StorageError(NoSuchElementException("Task not found")))
                }
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun saveTask(task: Task): Result<Unit> {
        return withContext(dispatchers.io) {
            try {
                taskDao.insert(task.toEntity())
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun updateTask(task: Task): Result<Unit> {
        return withContext(dispatchers.io) {
            try {
                taskDao.update(task.toEntity())
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun completeTask(taskId: String, isCompleted: Boolean): Result<Unit> {
        return withContext(dispatchers.io) {
            try {
                taskDao.markCompleted(taskId, isCompleted)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun deleteTask(taskId: String): Result<Unit> {
        return withContext(dispatchers.io) {
            try {
                taskDao.delete(taskId)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }
}
