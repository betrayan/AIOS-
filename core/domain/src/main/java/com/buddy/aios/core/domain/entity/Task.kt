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
    val recurrenceRule: String? = null, // e.g., "DAILY", "WEEKLY", "WEEKDAYS", null
    val deliveryState: ReminderDeliveryState = if (isCompleted) ReminderDeliveryState.COMPLETED else ReminderDeliveryState.SCHEDULED,
    val voiceEnabled: Boolean = true,
    val notificationEnabled: Boolean = true,
    val morningBriefingEligible: Boolean = true,
) {
    val category: TaskCategory
        get() = TaskCategory.fromText(title, description)

    val effectivePriority: TaskPriority
        get() = if (priority == TaskPriority.MEDIUM) TaskCategory.inferPriority(category) else priority

    val schedule: ReminderSchedule?
        get() {
            val trigger = reminderTime ?: dueDate ?: return null
            val rule = when (recurrenceRule?.uppercase()) {
                "DAILY" -> RepeatRule.DAILY
                "WEEKLY" -> RepeatRule.WEEKLY
                "WEEKDAYS" -> RepeatRule.WEEKDAYS
                "MORNING" -> RepeatRule.MORNING
                null, "" -> RepeatRule.ONE_TIME
                else -> RepeatRule.CUSTOM_RECURRING
            }
            return ReminderSchedule(
                triggerAt = trigger,
                timezone = timezone,
                repeatRule = rule,
                deliveryState = deliveryState,
                enabled = !isCompleted && status != TaskStatus.CANCELLED,
                notificationEnabled = notificationEnabled,
                voiceEnabled = voiceEnabled,
                morningBriefingEligible = morningBriefingEligible,
            )
        }
}
