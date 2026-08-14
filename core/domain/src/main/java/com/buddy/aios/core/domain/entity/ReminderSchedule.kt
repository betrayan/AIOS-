package com.buddy.aios.core.domain.entity

import java.util.TimeZone

/**
 * Supported recurrence rules for scheduled reminders.
 */
enum class RepeatRule {
    ONE_TIME,
    DAILY,
    WEEKLY,
    WEEKDAYS,
    CUSTOM_RECURRING,
    MORNING,
}

/**
 * Tracks lifecycle delivery state of scheduled reminders.
 */
enum class ReminderDeliveryState {
    SCHEDULED,
    TRIGGERED,
    DELIVERED,
    ACKNOWLEDGED,
    COMPLETED,
    SNOOZED,
    CANCELLED,
    MISSED,
}

/**
 * Delivery configuration and scheduling parameters for a task reminder.
 */
data class ReminderSchedule(
    val triggerAt: Long,
    val timezone: String = TimeZone.getDefault().id,
    val repeatRule: RepeatRule = RepeatRule.ONE_TIME,
    val deliveryState: ReminderDeliveryState = ReminderDeliveryState.SCHEDULED,
    val enabled: Boolean = true,
    val notificationEnabled: Boolean = true,
    val voiceEnabled: Boolean = true,
    val morningBriefingEligible: Boolean = true,
)
