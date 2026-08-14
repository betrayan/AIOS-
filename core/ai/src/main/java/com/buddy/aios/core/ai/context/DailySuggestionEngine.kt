package com.buddy.aios.core.ai.context

import com.buddy.aios.core.domain.entity.Task
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates actionable, context-grounded suggestions for the user's day.
 * NEVER outputs generic motivational quotes. Always references actual context.
 */
@Singleton
class DailySuggestionEngine @Inject constructor() {

    fun generateSuggestion(
        dayClassification: DayClassification,
        topPriorityItems: List<Task>,
        hasTravelContext: Boolean,
        hasWeatherAlert: Boolean,
    ): String {
        val topTask = topPriorityItems.firstOrNull()

        return when {
            hasTravelContext -> {
                "You have travel planned today. I'd double-check your departure time, pack early, and ensure your phone is fully charged."
            }

            hasWeatherAlert -> {
                "Weather warnings are active today. Plan your travel early and keep an umbrella or raincoat ready."
            }

            dayClassification == DayClassification.CODING_DAY && topTask != null -> {
                "You have a focused coding day ahead. I'd start with '${topTask.title}' while your morning focus is fresh."
            }

            dayClassification == DayClassification.STUDY_DAY && topTask != null -> {
                "Today is focused on study and preparation. Starting with '${topTask.title}' will set a strong pace for the day."
            }

            dayClassification == DayClassification.BUSY_DAY && topTask != null -> {
                "You have a busy schedule today. Prioritize '${topTask.title}' first before attending to secondary items."
            }

            dayClassification == DayClassification.LIGHT_DAY -> {
                "Your schedule looks light today. It's a great opportunity to relax or catch up on personal goals."
            }

            topTask != null -> {
                "I'd recommend starting with '${topTask.title}' while your morning is free."
            }

            else -> {
                "Ready to assist whenever you're set for today's goals."
            }
        }
    }
}
