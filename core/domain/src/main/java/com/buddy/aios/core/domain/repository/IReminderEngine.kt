package com.buddy.aios.core.domain.repository

import com.buddy.aios.core.domain.entity.ReminderSchedule
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.result.Result

sealed interface ReminderEngineResult {
    data class Success(val task: Task, val confirmationMessage: String) : ReminderEngineResult
    data class Failure(val reason: String) : ReminderEngineResult
    data class PermissionRequired(val permissionName: String, val message: String) : ReminderEngineResult
}

/**
 * Single master orchestration interface for AIOS Reminder scheduling & delivery.
 */
interface IReminderEngine {
    suspend fun createReminder(
        title: String,
        description: String = "",
        triggerTimestamp: Long?,
        recurrenceRule: String? = null,
        voiceEnabled: Boolean = true,
    ): ReminderEngineResult

    suspend fun scheduleReminder(task: Task): Boolean
    suspend fun cancelReminder(taskId: String): Boolean
    suspend fun rescheduleReminder(taskId: String, newTime: Long, repeatRule: String? = null): ReminderEngineResult
    suspend fun restoreReminders(): Int
    suspend fun handleReminderTriggered(taskId: String, occurrenceId: Int): Boolean
    suspend fun completeReminder(taskId: String): Boolean
    suspend fun snoozeReminder(taskId: String, snoozeMinutes: Int = 10): ReminderEngineResult
}
