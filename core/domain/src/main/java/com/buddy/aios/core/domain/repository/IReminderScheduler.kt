package com.buddy.aios.core.domain.repository

import com.buddy.aios.core.domain.entity.Task

/**
 * Interface for OS-level reminder alarm scheduling.
 */
interface IReminderScheduler {
    fun canScheduleExactAlarms(): Boolean
    fun schedule(task: Task): Boolean
    fun cancel(taskId: String, notificationId: Int)
    fun cancel(task: Task)
}
