package com.buddy.aios.core.ai.context

/**
 * Types of context items evaluated by the ContextRelevanceEngine.
 */
enum class ContextType {
    TASK,
    REMINDER,
    EVENT,
    WEATHER,
    WEATHER_ALERT,
    SLEEP,
    BATTERY,
    TRAVEL,
    MEMORY,
    USER_GOAL,
    SYSTEM,
    OTHER,
}

/**
 * Priority levels for context items.
 */
enum class ContextPriority(val weight: Int) {
    CRITICAL(4),
    HIGH(3),
    MEDIUM(2),
    LOW(1),
    OPTIONAL(0),
}

/**
 * Classification of the user's day based on analyzed context.
 */
enum class DayClassification {
    NORMAL_DAY,
    STUDY_DAY,
    WORK_DAY,
    CODING_DAY,
    PROJECT_DAY,
    TRAVEL_DAY,
    BUSY_DAY,
    LIGHT_DAY,
    IMPORTANT_DAY,
    DEADLINE_DAY,
}

/**
 * Data structure representing a single contextual factor with relevance and urgency scores.
 */
data class ContextItem(
    val id: String,
    val type: ContextType,
    val priority: ContextPriority,
    val relevanceScore: Float, // 0.0f to 1.0f
    val urgencyScore: Float,   // 0.0f to 1.0f
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val description: String = "",
    val reason: String = "",
    val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * Combined score used for ranking items (60% relevance, 40% urgency).
     */
    val combinedScore: Float
        get() = (relevanceScore * 0.6f) + (urgencyScore * 0.4f)
}
