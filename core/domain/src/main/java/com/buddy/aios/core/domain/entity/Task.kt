package com.buddy.aios.core.domain.entity

import java.util.TimeZone

enum class TaskPriority {
    LOW,
    MEDIUM,
    HIGH,
}

enum class TaskStatus {
    PENDING,
    COMPLETED,
    SNOOZED,
    CANCELLED,
    EXPIRED,
}

/**
 * Domain entity representing a user task or reminder managed by Buddy AI OS.
 */
data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long,
    val dueDate: Long? = null,
    val reminderTime: Long? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val tags: List<String> = emptyList(),
    val isReminder: Boolean = reminderTime != null,
    val notificationId: Int = id.hashCode(),
    val timezone: String = TimeZone.getDefault().id,
    val status: TaskStatus = if (isCompleted) TaskStatus.COMPLETED else TaskStatus.PENDING,
    val recurrenceRule: String? = null, // e.g., "DAILY", "WEEKLY", null
)
