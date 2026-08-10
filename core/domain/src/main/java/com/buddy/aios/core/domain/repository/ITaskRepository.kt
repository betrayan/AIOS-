package com.buddy.aios.core.domain.repository

import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for task and reminder management.
 */
interface ITaskRepository {

    /** Observe active (uncompleted) tasks ordered by due date/created date. */
    fun observeActiveTasks(): Flow<List<Task>>

    /** Observe completed tasks. */
    fun observeCompletedTasks(): Flow<List<Task>>

    /** Query upcoming tasks with due dates after [fromTimestamp]. */
    suspend fun getUpcomingTasks(fromTimestamp: Long = System.currentTimeMillis()): Result<List<Task>>

    /** Fetch a task by ID. */
    suspend fun getTaskById(taskId: String): Result<Task>

    /** Save or create a task. */
    suspend fun saveTask(task: Task): Result<Unit>

    /** Update task details. */
    suspend fun updateTask(task: Task): Result<Unit>

    /** Mark a task as completed. */
    suspend fun completeTask(taskId: String, isCompleted: Boolean = true): Result<Unit>

    /** Delete a task. */
    suspend fun deleteTask(taskId: String): Result<Unit>
}
