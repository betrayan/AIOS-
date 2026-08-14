package com.buddy.aios.core.ai.brain

import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Task
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects current situational states and flags from tasks, time, weather, battery, and mode.
 */
@Singleton
class SituationDetector @Inject constructor() {

    fun detectSituations(
        hourOfDay: Int,
        activeTasks: List<Task>,
        overdueTasks: List<Task>,
        upcomingReminders: List<Task>,
        batteryLevel: Int?,
        weatherCondition: String?,
        buddyMode: BuddyMode,
    ): Set<SituationFlag> {
        val flags = mutableSetOf<SituationFlag>()

        // Time of day
        when (hourOfDay) {
            in 5..11 -> flags.add(SituationFlag.MORNING)
            in 12..16 -> flags.add(SituationFlag.AFTERNOON)
            in 17..20 -> flags.add(SituationFlag.EVENING)
            in 21..23 -> flags.add(SituationFlag.NIGHT)
            else -> flags.add(SituationFlag.LATE_NIGHT)
        }

        // BuddyMode
        if (buddyMode == BuddyMode.QUIET || buddyMode == BuddyMode.SILENT) {
            flags.add(SituationFlag.QUIET_MODE)
        }

        // Travel detection
        val travelTask = activeTasks.firstOrNull { task ->
            val title = task.title.lowercase()
            title.contains("travel") || title.contains("flight") || title.contains("train") || title.contains("trip")
        }
        if (travelTask != null) {
            flags.add(SituationFlag.TRAVEL_DAY)
        }

        // Low battery
        if (batteryLevel != null && batteryLevel <= 20) {
            flags.add(SituationFlag.LOW_BATTERY)
        }

        // Rain risk
        if (weatherCondition != null && (weatherCondition.contains("Rain", ignoreCase = true) || weatherCondition.contains("Drizzle", ignoreCase = true) || weatherCondition.contains("Storm", ignoreCase = true))) {
            flags.add(SituationFlag.RAIN_RISK)
        }

        // Overdue & Missed tasks
        if (overdueTasks.isNotEmpty()) {
            flags.add(SituationFlag.OVERDUE_TASK)
        }

        // Multiple deadlines
        val urgentTasksCount = activeTasks.count { it.dueDate != null || it.reminderTime != null }
        if (urgentTasksCount >= 4) {
            flags.add(SituationFlag.MULTIPLE_DEADLINES)
            flags.add(SituationFlag.BUSY_DAY)
        } else if (activeTasks.size >= 5) {
            flags.add(SituationFlag.BUSY_DAY)
        } else {
            flags.add(SituationFlag.NORMAL_DAY)
        }

        // Study vs Work classification
        val studyCount = activeTasks.count { it.title.contains("study", ignoreCase = true) || it.title.contains("exam", ignoreCase = true) || it.title.contains("read", ignoreCase = true) }
        val codingCount = activeTasks.count { it.title.contains("code", ignoreCase = true) || it.title.contains("java", ignoreCase = true) || it.title.contains("python", ignoreCase = true) || it.title.contains("project", ignoreCase = true) }

        if (studyCount > codingCount && studyCount > 0) {
            flags.add(SituationFlag.STUDY_TIME)
        } else if (codingCount > 0) {
            flags.add(SituationFlag.WORK_TIME)
        }

        if (upcomingReminders.isNotEmpty()) {
            flags.add(SituationFlag.UPCOMING_REMINDER)
        }

        return flags
    }
}
