package com.buddy.aios.core.ai.context

import com.buddy.aios.core.analytics.activity.EstimatedSleepEstimate
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Information payload passed into the ContextRelevanceEngine.
 */
data class RawContextPayload(
    val tasks: List<Task> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val sleepEstimate: EstimatedSleepEstimate? = null,
    val batteryLevel: Int? = null, // e.g. 18 for 18%
    val isCharging: Boolean = false,
    val weatherCondition: String? = null, // e.g. "Rain", "Clear", "Thunderstorm"
    val temperatureCelsius: Int? = null,
    val isWeatherWarning: Boolean = false,
    val userExplicitQuery: String? = null,
)

/**
 * Intelligent Relevance Engine for AIOS.
 * Analyzes context sources, scores relevance & urgency, detects travel context,
 * classifies day types, and filters out noise (e.g., normal 85% battery).
 */
@Singleton
class ContextRelevanceEngine @Inject constructor() {

    companion object {
        private const val TAG = "ContextRelevanceEngine"

        private val TRAVEL_KEYWORDS = listOf(
            "travel", "trip", "flight", "train", "bus", "chennai", "bangalore", "delhi",
            "mumbai", "airport", "station", "hotel", "packing", "heading to", "destination",
            "tickets", "cab", "ride", "journey"
        )

        private val CODING_KEYWORDS = listOf(
            "code", "coding", "java", "kotlin", "python", "dsa", "aios", "bug", "git",
            "github", "project", "algorithm", "android", "docker", "api", "backend", "frontend"
        )

        private val STUDY_KEYWORDS = listOf(
            "study", "exam", "test", "college", "university", "assignment", "revision",
            "lecture", "subject", "chapter", "read", "notes", "quiz"
        )

        private val WORK_KEYWORDS = listOf(
            "meeting", "sync", "standup", "client", "office", "presentation", "report",
            "review", "deadline", "work", "email"
        )
    }

    /**
     * Main entry point to analyze all available raw context into scored ContextItems.
     */
    fun analyzeContext(payload: RawContextPayload): List<ContextItem> {
        val items = mutableListOf<ContextItem>()
        val now = System.currentTimeMillis()

        // 1. Travel Context Detection
        val travelItem = detectTravelContext(payload, now)
        travelItem?.let { items.add(it) }
        val hasTravelContext = travelItem != null

        // 2. Battery Context (Strict Relevance Rule: ONLY score high if battery low + travel/urgent event or explicit query)
        val batteryItem = evaluateBatteryContext(payload, hasTravelContext, items, now)
        batteryItem?.let { items.add(it) }

        // 3. Weather Context (Conversational: only when rain, severe warning, extreme heat, or travel)
        val weatherItem = evaluateWeatherContext(payload, hasTravelContext, now)
        weatherItem?.let { items.add(it) }

        // 4. Tasks & Reminders
        payload.tasks.forEach { task ->
            val taskItem = evaluateTaskContext(task, now)
            items.add(taskItem)
        }

        // 5. Sleep Context (Only if sufficient data exists)
        payload.sleepEstimate?.let { sleep ->
            if (sleep.hasSufficientData) {
                items.add(
                    ContextItem(
                        id = "sleep_estimate",
                        type = ContextType.SLEEP,
                        priority = ContextPriority.MEDIUM,
                        relevanceScore = 0.6f,
                        urgencyScore = 0.2f,
                        confidence = 0.9f,
                        timestamp = now,
                        title = "Estimated Sleep",
                        description = "${sleep.formattedDuration} (${sleep.comparisonToTargetText})",
                        reason = "Overnight activity estimate available",
                    )
                )
            }
        }

        // 6. Relevant Memories
        payload.memories.take(3).forEach { memory ->
            items.add(
                ContextItem(
                    id = "mem_${memory.id}",
                    type = ContextType.MEMORY,
                    priority = ContextPriority.LOW,
                    relevanceScore = 0.4f,
                    urgencyScore = 0.1f,
                    confidence = 0.8f,
                    timestamp = memory.createdAt,
                    title = "User Preference/Memory",
                    description = memory.summary,
                    reason = "Relevant long-term memory",
                )
            )
        }

        AppLogger.d(TAG, "Analyzed ${items.size} context items. Travel=$hasTravelContext")
        return rankContext(items)
    }

    /**
     * Ranks items by combined score (60% relevance + 40% urgency) descending.
     */
    fun rankContext(items: List<ContextItem>): List<ContextItem> {
        return items.sortedByDescending { it.combinedScore }
    }

    /**
     * Filters items that are IMPORTANT (priority >= HIGH or relevance >= 0.7).
     */
    fun identifyImportantContext(items: List<ContextItem>): List<ContextItem> {
        return items.filter { it.priority.weight >= ContextPriority.HIGH.weight || it.relevanceScore >= 0.7f }
    }

    /**
     * Filters items that are URGENT (urgency >= 0.7).
     */
    fun identifyUrgentContext(items: List<ContextItem>): List<ContextItem> {
        return items.filter { it.urgencyScore >= 0.7f }
    }

    /**
     * Filters items that are OPTIONAL/LOW (relevance < 0.4).
     */
    fun identifyOptionalContext(items: List<ContextItem>): List<ContextItem> {
        return items.filter { it.relevanceScore < 0.4f }
    }

    /**
     * Classifies the overall theme of the day based on tasks, reminders, and travel.
     */
    fun classifyDay(items: List<ContextItem>, tasks: List<Task>): DayClassification {
        val hasTravel = items.any { it.type == ContextType.TRAVEL }
        if (hasTravel) return DayClassification.TRAVEL_DAY

        val overdueCount = tasks.count { (it.reminderTime ?: it.dueDate ?: Long.MAX_VALUE) < System.currentTimeMillis() && !it.isCompleted }
        if (overdueCount >= 2 || tasks.size >= 6) return DayClassification.BUSY_DAY

        var codingCount = 0
        var studyCount = 0
        var workCount = 0

        tasks.forEach { task ->
            val titleLower = task.title.lowercase()
            if (CODING_KEYWORDS.any { titleLower.contains(it) }) codingCount++
            if (STUDY_KEYWORDS.any { titleLower.contains(it) }) studyCount++
            if (WORK_KEYWORDS.any { titleLower.contains(it) }) workCount++
        }

        return when {
            codingCount >= 2 -> DayClassification.CODING_DAY
            studyCount >= 2 -> DayClassification.STUDY_DAY
            workCount >= 2 -> DayClassification.WORK_DAY
            tasks.isEmpty() -> DayClassification.LIGHT_DAY
            else -> DayClassification.NORMAL_DAY
        }
    }

    // ── Helper Evaluation Methods ─────────────────────────────────────────────

    private fun detectTravelContext(payload: RawContextPayload, now: Long): ContextItem? {
        val matchingTasks = payload.tasks.filter { task ->
            val text = "${task.title} ${task.description}".lowercase()
            TRAVEL_KEYWORDS.any { text.contains(it) }
        }

        val matchingMemories = payload.memories.filter { memory ->
            val text = memory.summary.lowercase()
            TRAVEL_KEYWORDS.any { text.contains(it) }
        }

        if (matchingTasks.isEmpty() && matchingMemories.isEmpty()) return null

        val topTravelTask = matchingTasks.firstOrNull()
        val title = topTravelTask?.title ?: "Upcoming Trip"
        val dueTime = topTravelTask?.reminderTime ?: topTravelTask?.dueDate
        val timeDesc = dueTime?.let {
            " at ${SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(it))}"
        } ?: ""

        return ContextItem(
            id = "travel_context",
            type = ContextType.TRAVEL,
            priority = ContextPriority.HIGH,
            relevanceScore = 0.95f,
            urgencyScore = if (dueTime != null && dueTime - now < 12 * 3600_000L) 0.9f else 0.5f,
            confidence = 1.0f,
            timestamp = now,
            title = "Travel Context: $title",
            description = "Trip/Travel planned$timeDesc.",
            reason = "User created travel reminder or memory",
        )
    }

    private fun evaluateBatteryContext(
        payload: RawContextPayload,
        hasTravelContext: Boolean,
        currentItems: List<ContextItem>,
        now: Long
    ): ContextItem? {
        val level = payload.batteryLevel ?: return null
        val isExplicitQuery = payload.userExplicitQuery?.lowercase()?.contains("battery") == true

        // Rule: Normal battery (e.g. 85%) on normal day -> DO NOT MENTION (relevance 0)
        if (level > 25 && !isExplicitQuery) return null

        // Low battery (<20%) is relevant if travel exists or urgent event approaching
        val isLow = level <= 20
        val hasUrgentEvent = currentItems.any { it.urgencyScore >= 0.7f }

        val relevance = when {
            isExplicitQuery -> 1.0f
            isLow && (hasTravelContext || hasUrgentEvent) -> 0.9f
            isLow -> 0.7f
            else -> 0.2f
        }

        val priority = if (isLow && (hasTravelContext || hasUrgentEvent)) ContextPriority.HIGH else ContextPriority.LOW

        val msg = when {
            isLow && hasTravelContext -> "Your battery is at ${level}%. Since you're heading out, charge your phone before leaving."
            isLow && hasUrgentEvent -> "Your battery is at ${level}%. You have an important item coming up soon, so consider plugging in."
            isLow -> "Your battery is low at ${level}%."
            else -> "Battery level is ${level}%."
        }

        return ContextItem(
            id = "battery_context",
            type = ContextType.BATTERY,
            priority = priority,
            relevanceScore = relevance,
            urgencyScore = if (isLow) 0.8f else 0.1f,
            timestamp = now,
            title = "Battery Status (${level}%)",
            description = msg,
            reason = "Battery level evaluation rule",
        )
    }

    private fun evaluateWeatherContext(
        payload: RawContextPayload,
        hasTravelContext: Boolean,
        now: Long
    ): ContextItem? {
        val cond = payload.weatherCondition?.lowercase() ?: return null
        val temp = payload.temperatureCelsius
        val isWarning = payload.isWeatherWarning
        val isExplicitQuery = payload.userExplicitQuery?.lowercase()?.contains(Regex("(weather|rain|umbrella|hot|cold|temperature)")) == true

        val isRain = cond.contains("rain") || cond.contains("drizzle") || cond.contains("shower") || cond.contains("thunderstorm")
        val isHot = (temp ?: 0) >= 38

        // Ignore normal clear weather unless user explicitly asked or travel exists
        if (!isRain && !isHot && !isWarning && !hasTravelContext && !isExplicitQuery) return null

        val summary = when {
            isWarning -> "There's a weather warning today ($cond). Plan accordingly."
            isRain -> "Rain is possible today, so carry an umbrella."
            isHot -> "It's going to be quite hot (${temp}°C) today. Stay hydrated."
            else -> "Weather is $cond${temp?.let { " (${it}°C)" } ?: ""}."
        }

        val relevance = when {
            isExplicitQuery -> 1.0f
            isWarning -> 0.95f
            isRain && hasTravelContext -> 0.9f
            isRain -> 0.75f
            isHot -> 0.65f
            else -> 0.4f
        }

        return ContextItem(
            id = "weather_context",
            type = if (isWarning) ContextType.WEATHER_ALERT else ContextType.WEATHER,
            priority = if (isWarning || (isRain && hasTravelContext)) ContextPriority.HIGH else ContextPriority.MEDIUM,
            relevanceScore = relevance,
            urgencyScore = if (isWarning) 0.85f else 0.4f,
            timestamp = now,
            title = "Weather: ${payload.weatherCondition ?: "Forecast"}",
            description = summary,
            reason = "Weather condition evaluation rule",
        )
    }

    private fun evaluateTaskContext(task: Task, now: Long): ContextItem {
        val triggerTime = task.reminderTime ?: task.dueDate ?: Long.MAX_VALUE
        val isOverdue = triggerTime < now && !task.isCompleted
        val isImminent = triggerTime in now..(now + 3600_000L) // within next 1 hour

        val priority = when {
            isOverdue -> ContextPriority.CRITICAL
            isImminent || task.priority == TaskPriority.HIGH -> ContextPriority.HIGH
            task.priority == TaskPriority.MEDIUM -> ContextPriority.MEDIUM
            else -> ContextPriority.LOW
        }

        val relevance = when {
            isOverdue -> 0.95f
            isImminent -> 0.9f
            task.priority == TaskPriority.HIGH -> 0.8f
            else -> 0.6f
        }

        val urgency = when {
            isOverdue -> 1.0f
            isImminent -> 0.9f
            else -> 0.3f
        }

        val dueStr = if (triggerTime != Long.MAX_VALUE) {
            " at ${SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(triggerTime))}"
        } else ""

        return ContextItem(
            id = "task_${task.id}",
            type = ContextType.REMINDER,
            priority = priority,
            relevanceScore = relevance,
            urgencyScore = urgency,
            timestamp = task.createdAt,
            title = task.title,
            description = "${task.title}$dueStr",
            reason = if (isOverdue) "Overdue task" else "Scheduled task",
            metadata = mapOf(
                "taskId" to task.id,
                "isCompleted" to task.isCompleted.toString(),
                "isOverdue" to isOverdue.toString(),
            )
        )
    }
}
