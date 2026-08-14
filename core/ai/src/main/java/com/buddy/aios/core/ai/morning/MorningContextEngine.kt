package com.buddy.aios.core.ai.morning

import com.buddy.aios.core.ai.context.ContextItem
import com.buddy.aios.core.ai.context.ContextRelevanceEngine
import com.buddy.aios.core.ai.context.ContextType
import com.buddy.aios.core.ai.context.DailySuggestionEngine
import com.buddy.aios.core.ai.context.DayClassification
import com.buddy.aios.core.ai.context.RawContextPayload
import com.buddy.aios.core.analytics.activity.DeviceActivityManager
import com.buddy.aios.core.analytics.activity.EstimatedSleepEstimate
import com.buddy.aios.core.analytics.activity.SleepActivityInference
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.UserProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backward-compatible MorningSummary object consumed by HomeScreen.
 */
data class MorningSummary(
    val isMorningWindowActive: Boolean,
    val greeting: String,
    val sleepEstimate: EstimatedSleepEstimate,
    val priorityTasks: List<Task>,
    val morningSuggestion: String,
    val spokenBriefing: String,
    val dayClassification: DayClassification = DayClassification.NORMAL_DAY,
)

/**
 * Evaluates device activity, tasks, memories, weather, battery, and time of day to assemble
 * an intelligent, privacy-first Morning Context and Briefing.
 */
@Singleton
class MorningContextEngine @Inject constructor(
    private val activityManager: DeviceActivityManager,
    private val sleepInference: SleepActivityInference,
    private val relevanceEngine: ContextRelevanceEngine,
    private val suggestionEngine: DailySuggestionEngine,
) {
    companion object {
        private const val TAG = "MorningContextEngine"
    }

    /**
     * Builds complete structured MorningBriefing payload.
     */
    fun generateMorningBriefing(
        userProfile: UserProfile?,
        activeTasks: List<Task>,
        memories: List<Memory>,
        batteryLevel: Int? = null,
        isCharging: Boolean = false,
        weatherCondition: String? = null,
        temperatureCelsius: Int? = null,
        isWeatherWarning: Boolean = false,
        targetSleepHours: Int = 8,
    ): MorningBriefing {
        val nowCal = Calendar.getInstance()
        val hour = nowCal.get(Calendar.HOUR_OF_DAY)
        val isMorningTime = hour in 5..10

        val snapshot = activityManager.snapshot.value
        val sleepEstimate = sleepInference.inferSleepEstimate(snapshot, targetSleepHours)

        // 1. Analyze Context via ContextRelevanceEngine
        val payload = RawContextPayload(
            tasks = activeTasks,
            memories = memories,
            sleepEstimate = sleepEstimate,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            weatherCondition = weatherCondition,
            temperatureCelsius = temperatureCelsius,
            isWeatherWarning = isWeatherWarning,
        )

        val analyzedItems = relevanceEngine.analyzeContext(payload)
        val dayClassification = relevanceEngine.classifyDay(analyzedItems, activeTasks)
        val importantItems = relevanceEngine.identifyImportantContext(analyzedItems)

        val displayName = userProfile?.preferredName?.ifBlank { userProfile.name } ?: "Buddy"

        val timeSalutation = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }

        // Contextual Greeting
        val greeting = when (dayClassification) {
            DayClassification.TRAVEL_DAY -> "$timeSalutation, $displayName. You've got a trip coming up today."
            DayClassification.BUSY_DAY -> "$timeSalutation, $displayName. You've got a busy day ahead."
            DayClassification.CODING_DAY -> "$timeSalutation, $displayName. You've got a focused coding day today."
            DayClassification.STUDY_DAY -> "$timeSalutation, $displayName. You've got a focused study day today."
            DayClassification.LIGHT_DAY -> "$timeSalutation, $displayName. Your day looks pretty light."
            else -> "$timeSalutation, $displayName"
        }

        // Sleep Summary
        val sleepSummary = if (sleepEstimate.hasSufficientData) {
            "You got around ${sleepEstimate.formattedDuration} of estimated sleep."
        } else null

        // Weather Summary (only if relevant context item exists)
        val weatherItem = analyzedItems.firstOrNull { it.type == ContextType.WEATHER || it.type == ContextType.WEATHER_ALERT }
        val weatherSummary = weatherItem?.description

        // Battery Message (only if relevant context item exists)
        val batteryItem = analyzedItems.firstOrNull { it.type == ContextType.BATTERY }
        val batteryMessage = batteryItem?.description

        // Travel Message (only if travel context exists)
        val travelItem = analyzedItems.firstOrNull { it.type == ContextType.TRAVEL }
        val travelMessage = travelItem?.description

        // Tasks & Priority Summary
        val priorityTasks = activeTasks.take(3)
        val prioritySummary = if (priorityTasks.isNotEmpty()) {
            val topTask = priorityTasks.first()
            val timeStr = topTask.reminderTime?.let {
                " (${com.buddy.aios.core.common.time.ReminderDateFormatter.formatNaturalDateTime(it)})"
            } ?: ""
            "You have ${priorityTasks.size} priority item${if (priorityTasks.size > 1) "s" else ""} today, starting with '${topTask.title}'$timeStr."
        } else "Your schedule is clear right now."

        // Grounded Daily Suggestion
        val suggestion = suggestionEngine.generateSuggestion(
            dayClassification = dayClassification,
            topPriorityItems = priorityTasks,
            hasTravelContext = travelItem != null,
            hasWeatherAlert = isWeatherWarning,
        )

        // Generate Conversational 15-30s Spoken Voice Summary (omits non-relevant details!)
        val spokenBriefing = buildSpokenBriefing(
            displayName = displayName,
            greeting = greeting,
            sleepSummary = sleepSummary,
            weatherSummary = weatherSummary,
            travelMessage = travelMessage,
            batteryMessage = batteryMessage,
            prioritySummary = prioritySummary,
            suggestion = suggestion,
        )

        // Notification formatting
        val notificationTitle = "Good morning, $displayName ☀️"
        val notificationBody = buildNotificationBody(priorityTasks, weatherItem, travelItem)

        AppLogger.d(TAG, "Generated MorningBriefing: dayClass=$dayClassification, items=${analyzedItems.size}")

        return MorningBriefing(
            greeting = greeting,
            dayClassification = dayClassification,
            sleepSummary = sleepSummary,
            weatherSummary = weatherSummary,
            prioritySummary = prioritySummary,
            importantReminders = priorityTasks,
            contextualBatteryMessage = batteryMessage,
            travelMessage = travelMessage,
            scheduleSummary = if (activeTasks.isNotEmpty()) "${activeTasks.size} tasks total" else "Clear schedule",
            suggestion = suggestion,
            closing = "Have a great day!",
            spokenBriefing = spokenBriefing,
            notificationTitle = notificationTitle,
            notificationBody = notificationBody,
            isMorningWindowActive = isMorningTime,
        )
    }

    /**
     * Backward-compatible method for HomeScreen.
     */
    fun generateMorningSummary(
        userProfile: UserProfile?,
        activeTasks: List<Task>,
        memories: List<Memory>,
        targetSleepHours: Int = 8,
    ): MorningSummary {
        val briefing = generateMorningBriefing(
            userProfile = userProfile,
            activeTasks = activeTasks,
            memories = memories,
            targetSleepHours = targetSleepHours,
        )

        val snapshot = activityManager.snapshot.value
        val sleepEstimate = sleepInference.inferSleepEstimate(snapshot, targetSleepHours)

        return MorningSummary(
            isMorningWindowActive = briefing.isMorningWindowActive,
            greeting = briefing.greeting,
            sleepEstimate = sleepEstimate,
            priorityTasks = briefing.importantReminders,
            morningSuggestion = briefing.suggestion ?: "Ready when you are.",
            spokenBriefing = briefing.spokenBriefing,
            dayClassification = briefing.dayClassification,
        )
    }

    // ── Private Voice & Notification Assemblers ───────────────────────────────

    private fun buildSpokenBriefing(
        displayName: String,
        greeting: String,
        sleepSummary: String?,
        weatherSummary: String?,
        travelMessage: String?,
        batteryMessage: String?,
        prioritySummary: String,
        suggestion: String?,
    ): String = buildString {
        append(greeting.removeSuffix("☀️").trim()).append(". ")

        sleepSummary?.let { append(it).append(" ") }
        travelMessage?.let { append(it).append(" ") }
        weatherSummary?.let { append(it).append(" ") }
        batteryMessage?.let { append(it).append(" ") }

        append(prioritySummary).append(" ")

        suggestion?.let { append(it).append(" ") }

        append("Have a great day!")
    }.trim()

    private fun buildNotificationBody(
        priorityTasks: List<Task>,
        weatherItem: ContextItem?,
        travelItem: ContextItem?,
    ): String = buildString {
        if (travelItem != null) {
            append("✈️ Trip planned · ")
        }
        if (priorityTasks.isNotEmpty()) {
            append("${priorityTasks.size} priority item${if (priorityTasks.size > 1) "s" else ""}")
            priorityTasks.firstOrNull()?.reminderTime?.let {
                val timeStr = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(it))
                append(" · First at $timeStr")
            }
        } else {
            append("Schedule clear this morning")
        }

        weatherItem?.let {
            if (it.description.contains("Rain", ignoreCase = true)) {
                append(" · 🌧️ Rain possible")
            }
        }
    }
}
