package com.buddy.aios.core.ai.brain

import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.UserProfile

/**
 * Modes for AIOS proactive intelligence assistance.
 */
enum class ProactiveMode {
    FULL,       // Proactive on important and medium items
    BALANCED,   // Default: Proactive on important context items only
    QUIET,      // Critical notifications only
    OFF,        // No proactive notifications
}

/**
 * Priority levels for tasks and actions.
 */
enum class PriorityLevel {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    BACKGROUND,
}

/**
 * Situational flags detected from context.
 */
enum class SituationFlag {
    NORMAL_DAY,
    BUSY_DAY,
    STUDY_TIME,
    WORK_TIME,
    TRAVEL_DAY,
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
    LATE_NIGHT,
    LOW_BATTERY,
    RAIN_RISK,
    OVERDUE_TASK,
    UPCOMING_REMINDER,
    MISSED_TASK,
    MULTIPLE_DEADLINES,
    QUIET_MODE,
}

/**
 * Unified context snapshot capturing current device state, active data, and situational flags.
 */
data class AIOSContextSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val hourOfDay: Int,
    val timeSalutation: String,
    val userProfile: UserProfile?,
    val buddyMode: BuddyMode = BuddyMode.ACTIVE,
    val proactiveMode: ProactiveMode = ProactiveMode.BALANCED,
    val activeTasks: List<Task> = emptyList(),
    val overdueTasks: List<Task> = emptyList(),
    val upcomingReminders: List<Task> = emptyList(),
    val relevantMemories: List<Memory> = emptyList(),
    val situations: Set<SituationFlag> = emptySet(),
    val batteryLevel: Int? = null,
    val isCharging: Boolean = false,
    val weatherCondition: String? = null,
    val temperatureCelsius: Int? = null,
    val travelDestination: String? = null,
    val travelTime: String? = null,
    val recentConversationTopic: String? = null,
)
