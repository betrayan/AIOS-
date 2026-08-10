package com.buddy.aios.core.domain.entity

enum class TaskPriority {
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * Domain entity representing a user task or reminder managed by Buddy.
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
)
