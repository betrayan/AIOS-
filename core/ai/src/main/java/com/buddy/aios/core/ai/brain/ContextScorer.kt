package com.buddy.aios.core.ai.brain

import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.entity.Task
import javax.inject.Inject
import javax.inject.Singleton

data class ScoredContext(
    val relevantTasks: List<Task>,
    val relevantReminders: List<Task>,
    val relevantMemories: List<Memory>,
    val includeWeather: Boolean,
    val includeBattery: Boolean,
    val includeTravel: Boolean,
    val totalScore: Float,
)

/**
 * Context relevance scorer and context budget governor.
 * Prevents flooding Gemini AI with irrelevant context (e.g. omits battery/weather when asking a coding question).
 */
@Singleton
class ContextScorer @Inject constructor() {

    fun scoreAndFilterContext(
        query: String,
        intent: UserIntent,
        snapshot: AIOSContextSnapshot,
    ): ScoredContext {
        val q = query.lowercase()

        val isCodingOrStudyQuery = q.contains("recursion") || q.contains("code") || q.contains("java") || q.contains("python") || q.contains("explain") || intent == UserIntent.EXPLANATION
        val isTravelQuery = q.contains("travel") || q.contains("chennai") || q.contains("trip") || q.contains("flight") || snapshot.situations.contains(SituationFlag.TRAVEL_DAY)
        val isScheduleQuery = q.contains("what should i do") || q.contains("plan my day") || q.contains("important today") || intent == UserIntent.GENERAL_QUERY

        // 1. Task & Reminder relevance
        val scoredTasks = snapshot.activeTasks.sortedByDescending { task ->
            var score = 0.5f
            if (task.dueDate != null || task.reminderTime != null) score += 0.3f
            if (q.contains(task.title.lowercase())) score += 0.4f
            score
        }

        // 2. Memory relevance
        val scoredMemories = snapshot.relevantMemories.filter { memory ->
            if (isCodingOrStudyQuery) {
                memory.summary.contains("interview", ignoreCase = true) || memory.summary.contains("python", ignoreCase = true) || memory.summary.contains("java", ignoreCase = true) || memory.summary.contains("study", ignoreCase = true)
            } else {
                true
            }
        }.take(5)

        // 3. Situational weather relevance
        val includeWeather = when {
            q.contains("weather") || q.contains("rain") || q.contains("temperature") -> true
            isTravelQuery -> true
            snapshot.situations.contains(SituationFlag.RAIN_RISK) && (snapshot.situations.contains(SituationFlag.TRAVEL_DAY) || q.contains("outside")) -> true
            isCodingOrStudyQuery -> false
            else -> false
        }

        // 4. Situational battery relevance
        val includeBattery = when {
            q.contains("battery") || q.contains("charge") -> true
            isTravelQuery && snapshot.situations.contains(SituationFlag.LOW_BATTERY) -> true
            isCodingOrStudyQuery -> false
            else -> false
        }

        // 5. Travel relevance
        val includeTravel = isTravelQuery || snapshot.situations.contains(SituationFlag.TRAVEL_DAY)

        return ScoredContext(
            relevantTasks = scoredTasks.take(6),
            relevantReminders = snapshot.upcomingReminders.take(4),
            relevantMemories = scoredMemories,
            includeWeather = includeWeather,
            includeBattery = includeBattery,
            includeTravel = includeTravel,
            totalScore = 1.0f
        )
    }
}
