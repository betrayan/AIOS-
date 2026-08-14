package com.buddy.aios.core.ai.brain

import com.buddy.aios.core.ai.agent.GoalAnalyzer
import com.buddy.aios.core.ai.context.ConversationContextManager
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.UserProfile
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Master Personal Intelligence Engine for AIOS (Stage 8 Brain).
 * Coordinates situation detection, priority evaluation, decision making,
 * tool selection, anti-nagging proactive policies, and natural responses.
 */
@Singleton
class PersonalIntelligenceEngine @Inject constructor(
    private val situationDetector: SituationDetector,
    private val contextScorer: ContextScorer,
    private val priorityEngine: PriorityEngine,
    private val decisionEngine: DecisionEngine,
    private val goalAnalyzer: GoalAnalyzer,
    private val proactivePolicy: ProactiveNotificationPolicy,
    private val conversationContextManager: ConversationContextManager,
) {
    companion object {
        private const val TAG = "PersonalIntelligenceEngine"
    }

    suspend fun buildSnapshot(
        userProfile: UserProfile?,
        buddyMode: BuddyMode,
        activeTasks: List<Task>,
        memories: List<Memory>,
        batteryLevel: Int? = null,
        isCharging: Boolean = false,
        weatherCondition: String? = null,
        temperatureCelsius: Int? = null,
        conversationId: String? = null,
    ): AIOSContextSnapshot {
        val nowCal = Calendar.getInstance()
        val hour = nowCal.get(Calendar.HOUR_OF_DAY)
        val timeSalutation = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }

        val overdueTasks = activeTasks.filter { (it.reminderTime ?: it.dueDate ?: Long.MAX_VALUE) < System.currentTimeMillis() }
        val upcomingReminders = activeTasks.filter { (it.reminderTime ?: 0L) > System.currentTimeMillis() }

        val situations = situationDetector.detectSituations(
            hourOfDay = hour,
            activeTasks = activeTasks,
            overdueTasks = overdueTasks,
            upcomingReminders = upcomingReminders,
            batteryLevel = batteryLevel,
            weatherCondition = weatherCondition,
            buddyMode = buddyMode,
        )

        val topic = conversationId?.let { id -> conversationContextManager.getOrCreateContext(id).currentTopic }

        return AIOSContextSnapshot(
            hourOfDay = hour,
            timeSalutation = timeSalutation,
            userProfile = userProfile,
            buddyMode = buddyMode,
            proactiveMode = proactivePolicy.getProactiveMode(),
            activeTasks = activeTasks,
            overdueTasks = overdueTasks,
            upcomingReminders = upcomingReminders,
            relevantMemories = memories,
            situations = situations,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            weatherCondition = weatherCondition,
            temperatureCelsius = temperatureCelsius,
            recentConversationTopic = topic,
        )
    }

    suspend fun processQuery(
        query: String,
        snapshot: AIOSContextSnapshot,
    ): ActionDecision {
        val q = query.lowercase()
        val intent = when {
            q.contains("what should i do") -> UserIntent.WHAT_SHOULD_I_DO_NOW
            q.contains("what is important") || q.contains("key tasks") -> UserIntent.WHAT_IS_IMPORTANT_TODAY
            q.contains("plan my day") || q.contains("schedule today") -> UserIntent.DAILY_PLAN
            q.contains("why did you suggest") || q.contains("why charge") -> UserIntent.EXPLANATION_WHY
            q.contains("travel") || q.contains("flight") || q.contains("chennai") -> UserIntent.TRAVEL
            q.contains("weather") || q.contains("rain") -> UserIntent.CONTEXT_QUERY
            else -> UserIntent.GENERAL_QUERY
        }

        val scoredContext = contextScorer.scoreAndFilterContext(query, intent, snapshot)
        val decision = decisionEngine.evaluateDecision(query, intent, snapshot, scoredContext)

        AppLogger.d(TAG, "Processed query='$query' -> Intent=$intent, Action=${decision.actionType}, Confidence=${decision.confidence}")
        return decision
    }

    fun getProactiveNotificationPolicy(): ProactiveNotificationPolicy = proactivePolicy
}
