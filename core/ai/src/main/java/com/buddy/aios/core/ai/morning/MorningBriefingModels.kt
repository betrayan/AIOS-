package com.buddy.aios.core.ai.morning

import com.buddy.aios.core.ai.context.ContextItem
import com.buddy.aios.core.ai.context.DayClassification
import com.buddy.aios.core.domain.entity.Task

/**
 * Assembled morning context payload before briefing generation.
 */
data class MorningContext(
    val greeting: String,
    val dayClassification: DayClassification,
    val sleepSummary: String? = null,
    val weatherSummary: String? = null,
    val prioritySummary: String? = null,
    val importantReminders: List<Task> = emptyList(),
    val contextualBatteryMessage: String? = null,
    val travelMessage: String? = null,
    val scheduleSummary: String? = null,
    val suggestion: String? = null,
    val closing: String = "Have a great day!",
    val contextItems: List<ContextItem> = emptyList(),
    val isMorningWindowActive: Boolean = true,
)

/**
 * Final structured Morning Briefing object with optional fields and conversational voice summary.
 */
data class MorningBriefing(
    val greeting: String,
    val dayClassification: DayClassification,
    val sleepSummary: String? = null,
    val weatherSummary: String? = null,
    val prioritySummary: String? = null,
    val importantReminders: List<Task> = emptyList(),
    val contextualBatteryMessage: String? = null,
    val travelMessage: String? = null,
    val scheduleSummary: String? = null,
    val suggestion: String? = null,
    val closing: String = "Have a great day!",
    val spokenBriefing: String,
    val notificationTitle: String,
    val notificationBody: String,
    val isMorningWindowActive: Boolean = true,
)
