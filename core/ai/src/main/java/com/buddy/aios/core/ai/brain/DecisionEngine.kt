package com.buddy.aios.core.ai.brain

import com.buddy.aios.core.domain.entity.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core decision maker for Personal Intelligence Brain.
 * Evaluates queries, context, intent, priorities, and ambiguity to determine ActionType.
 */
@Singleton
class DecisionEngine @Inject constructor(
    private val priorityEngine: PriorityEngine,
) {

    fun evaluateDecision(
        userQuery: String,
        intent: UserIntent,
        snapshot: AIOSContextSnapshot,
        scoredContext: ScoredContext,
    ): ActionDecision {
        val q = userQuery.lowercase().trim()

        // 1. "What should I do now?"
        if (q.contains("what should i do") || q.contains("what to do now")) {
            return resolveWhatShouldIDoNow(snapshot)
        }

        // 2. "What is important today?"
        if (q.contains("what is important") || q.contains("what's important") || q.contains("key tasks")) {
            return resolveWhatIsImportantToday(snapshot)
        }

        // 3. "Plan my day" / "Organize my day"
        if (q.contains("plan my day") || q.contains("what's my plan") || q.contains("schedule today")) {
            return resolvePlanMyDay(snapshot)
        }

        // 4. "Why did you suggest this?" / "Why are you suggesting this?"
        if (q.contains("why did you suggest") || q.contains("why are you suggesting") || q.contains("why charge")) {
            return resolveExplanationWhy(snapshot)
        }

        // 5. Ambiguity Check for Task/Reminder modification ("Move that to 8")
        if ((q.startsWith("move that") || q.startsWith("change that") || q.startsWith("delete that")) && snapshot.activeTasks.size > 1) {
            val candidates = snapshot.activeTasks.take(3).joinToString(", ") { "'${it.title}'" }
            return ActionDecision(
                actionType = ActionType.ASK_CLARIFICATION,
                confidence = DecisionConfidence.LOW,
                primaryTextResponse = "Which item would you like me to move? You have $candidates.",
                clarificationQuestion = "Which reminder or task do you want to change?",
            )
        }

        // 6. Default to standard AI execution / tool evaluation
        return ActionDecision(
            actionType = ActionType.ANSWER,
            confidence = DecisionConfidence.HIGH,
            primaryTextResponse = "",
        )
    }

    private fun resolveWhatShouldIDoNow(snapshot: AIOSContextSnapshot): ActionDecision {
        if (snapshot.activeTasks.isEmpty()) {
            val msg = "${snapshot.timeSalutation}! You currently have no pending tasks. Enjoy your time or tell me what you'd like to accomplish next."
            return ActionDecision(
                actionType = ActionType.ANSWER,
                confidence = DecisionConfidence.HIGH,
                primaryTextResponse = msg,
                voiceTextResponse = msg,
            )
        }

        val topRanked = priorityEngine.selectTopPriorities(snapshot.activeTasks, maxItems = 1).firstOrNull()
        val task = topRanked?.task ?: snapshot.activeTasks.first()

        val nextReminder = snapshot.upcomingReminders.firstOrNull()
        val responseText = if (nextReminder != null && nextReminder.id != task.id) {
            val reminderTimeStr = formatTime(nextReminder.reminderTime ?: System.currentTimeMillis())
            "Your highest priority right now is '${task.title}'. You also have an upcoming reminder for '${nextReminder.title}' at $reminderTimeStr."
        } else {
            "Your highest priority right now is to focus on '${task.title}' (${topRanked?.urgencyReason ?: "active task"})."
        }

        return ActionDecision(
            actionType = ActionType.ANSWER,
            confidence = DecisionConfidence.HIGH,
            primaryTextResponse = responseText,
            voiceTextResponse = responseText,
            targetTask = task,
            priority = topRanked?.priorityLevel ?: PriorityLevel.HIGH,
        )
    }

    private fun resolveWhatIsImportantToday(snapshot: AIOSContextSnapshot): ActionDecision {
        val topPriorities = priorityEngine.selectTopPriorities(snapshot.activeTasks, maxItems = 3)

        if (topPriorities.isEmpty()) {
            val msg = "Your schedule is clear today with no urgent tasks or reminders."
            return ActionDecision(actionType = ActionType.ANSWER, confidence = DecisionConfidence.HIGH, primaryTextResponse = msg, voiceTextResponse = msg)
        }

        val itemsList = topPriorities.joinToString("\n• ") { "${it.task.title} (${it.urgencyReason})" }
        val responseText = "Here are your top priorities for today:\n• $itemsList"
        val voiceText = "Your main priorities today are " + topPriorities.joinToString(" and ") { it.task.title } + "."

        return ActionDecision(
            actionType = ActionType.ANSWER,
            confidence = DecisionConfidence.HIGH,
            primaryTextResponse = responseText,
            voiceTextResponse = voiceText,
        )
    }

    private fun resolvePlanMyDay(snapshot: AIOSContextSnapshot): ActionDecision {
        if (snapshot.activeTasks.isEmpty() && snapshot.upcomingReminders.isEmpty()) {
            val msg = "I don't have any scheduled tasks or reminders for today. Tell me what you want to add, and I'll build your schedule."
            return ActionDecision(actionType = ActionType.ANSWER, confidence = DecisionConfidence.HIGH, primaryTextResponse = msg, voiceTextResponse = msg)
        }

        val sortedItems = (snapshot.activeTasks + snapshot.upcomingReminders)
            .distinctBy { it.id }
            .sortedBy { it.reminderTime ?: it.dueDate ?: Long.MAX_VALUE }

        val scheduleLines = sortedItems.joinToString("\n") { task ->
            val rTime = task.reminderTime
            val timeStr = if (rTime != null && rTime > 0) formatTime(rTime) else "Flexible"
            "• $timeStr: ${task.title}"
        }

        val responseText = "Here is your plan for today based on your active tasks:\n$scheduleLines"
        val voiceText = "Here is your day plan. You have ${sortedItems.size} items scheduled today starting with ${sortedItems.first().title}."

        return ActionDecision(
            actionType = ActionType.ANSWER,
            confidence = DecisionConfidence.HIGH,
            primaryTextResponse = responseText,
            voiceTextResponse = voiceText,
        )
    }

    private fun resolveExplanationWhy(snapshot: AIOSContextSnapshot): ActionDecision {
        val hasTravel = snapshot.situations.contains(SituationFlag.TRAVEL_DAY)
        val hasLowBattery = snapshot.situations.contains(SituationFlag.LOW_BATTERY)
        val hasRain = snapshot.situations.contains(SituationFlag.RAIN_RISK)

        val explanation = when {
            hasTravel && hasLowBattery -> "I suggested charging your phone because you have upcoming travel and your battery level is currently low (${snapshot.batteryLevel}%)."
            hasTravel && hasRain -> "I suggested preparing an umbrella because rain is expected during your upcoming trip."
            hasLowBattery -> "I gave that suggestion because your device battery is at ${snapshot.batteryLevel}%."
            else -> "I suggested that based on your upcoming scheduled priorities and current local time."
        }

        return ActionDecision(
            actionType = ActionType.ANSWER,
            confidence = DecisionConfidence.HIGH,
            primaryTextResponse = explanation,
            voiceTextResponse = explanation,
            userExplanation = explanation,
        )
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(timestamp))
    }
}
