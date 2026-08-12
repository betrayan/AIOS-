package com.buddy.aios.core.ai.morning

import com.buddy.aios.core.analytics.activity.DeviceActivityManager
import com.buddy.aios.core.analytics.activity.EstimatedSleepEstimate
import com.buddy.aios.core.analytics.activity.MorningReadiness
import com.buddy.aios.core.analytics.activity.SleepActivityInference
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.UserProfile
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class MorningSummary(
    val isMorningWindowActive: Boolean,
    val greeting: String,
    val sleepEstimate: EstimatedSleepEstimate,
    val priorityTasks: List<Task>,
    val morningSuggestion: String,
    val spokenBriefing: String,
)

/**
 * Evaluates device activity, tasks, memories, and time of day to assemble
 * a personal, privacy-first Morning Summary.
 */
@Singleton
class MorningContextEngine @Inject constructor(
    private val activityManager: DeviceActivityManager,
    private val sleepInference: SleepActivityInference,
) {
    companion object {
        private const val TAG = "MorningContextEngine"
    }

    fun generateMorningSummary(
        userProfile: UserProfile?,
        activeTasks: List<Task>,
        memories: List<Memory>,
        targetSleepHours: Int = 8,
    ): MorningSummary {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val isMorningTime = hour in 5..10

        val snapshot = activityManager.snapshot.value
        val sleepEstimate = sleepInference.inferSleepEstimate(snapshot, targetSleepHours)

        val displayName = userProfile?.preferredName?.ifBlank { userProfile.name } ?: "Buddy"
        val greeting = if (isMorningTime) "Good morning, $displayName ☀️" else "Hello, $displayName"

        val priorityTasks = activeTasks.take(3)

        // 1. Morning Suggestion Logic
        val topTask = priorityTasks.firstOrNull()
        val suggestion = when {
            topTask != null -> "You've got ${activeTasks.size} task${if (activeTasks.size > 1) "s" else ""} planned. I'd start with '${topTask.title}' while your focus is fresh."
            memories.any { it.summary.contains("interview", ignoreCase = true) } -> "Your interview preparation is still your biggest priority today. Shall we review practice questions?"
            else -> "Ready to get started on today's goals?"
        }

        // 2. Conversational Voice Briefing (natural, no raw stat dumping!)
        val spokenBriefing = buildString {
            append("Good morning, $displayName. ")
            if (sleepEstimate.hasSufficientData) {
                append("You got roughly ${sleepEstimate.formattedDuration} of estimated sleep. ")
            } else {
                append("I don't have enough overnight activity data to estimate your sleep yet. ")
            }

            if (priorityTasks.isNotEmpty()) {
                append("You have ${priorityTasks.size} priority item${if (priorityTasks.size > 1) "s" else ""} today")
                topTask?.let { append(", starting with ${it.title}") }
                append(". ")
            } else {
                append("Your schedule is clear right now. ")
            }

            if (topTask != null) {
                append("I'd recommend starting with ${topTask.title}.")
            }
        }

        AppLogger.d(TAG, "Generated MorningSummary: isMorning=$isMorningTime, tasks=${priorityTasks.size}")

        return MorningSummary(
            isMorningWindowActive = isMorningTime,
            greeting = greeting,
            sleepEstimate = sleepEstimate,
            priorityTasks = priorityTasks,
            morningSuggestion = suggestion,
            spokenBriefing = spokenBriefing,
        )
    }
}
