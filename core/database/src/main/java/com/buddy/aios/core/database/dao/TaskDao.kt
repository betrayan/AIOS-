package com.buddy.aios.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.buddy.aios.core.database.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE is_completed = 0 ORDER BY CASE WHEN due_date IS NULL THEN 1 ELSE 0 END, due_date ASC, created_at DESC")
    fun observeActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE is_completed = 1 ORDER BY created_at DESC")
    fun observeCompletedTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE due_date IS NOT NULL AND due_date >= :fromTimestamp ORDER BY due_date ASC")
    suspend fun getUpcomingTasks(fromTimestamp: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE reminder_time IS NOT NULL AND is_completed = 0 ORDER BY reminder_time ASC")
    suspend fun getPendingReminders(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET is_completed = :isCompleted, status = CASE WHEN :isCompleted = 1 THEN 'COMPLETED' ELSE 'PENDING' END, delivery_state = CASE WHEN :isCompleted = 1 THEN 'COMPLETED' ELSE 'SCHEDULED' END WHERE id = :id")
    suspend fun markCompleted(id: String, isCompleted: Boolean)

    @Query("UPDATE tasks SET reminder_time = :newReminderTime, status = :newStatus, delivery_state = :deliveryState WHERE id = :id")
    suspend fun updateReminderSchedule(id: String, newReminderTime: Long, newStatus: String, deliveryState: String)

    @Query("UPDATE tasks SET reminder_time = :newReminderTime, status = :newStatus WHERE id = :id")
    suspend fun updateReminderTime(id: String, newReminderTime: Long, newStatus: String)

    @Query("UPDATE tasks SET delivery_state = :deliveryState WHERE id = :id")
    suspend fun updateDeliveryState(id: String, deliveryState: String)

    @Query("SELECT * FROM tasks WHERE reminder_time IS NOT NULL AND reminder_time < :now AND is_completed = 0 AND delivery_state = 'SCHEDULED'")
    suspend fun getMissedReminders(now: Long): List<TaskEntity>

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)
}
