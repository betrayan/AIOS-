package com.buddy.aios.core.ai.agent

import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.agent.ActionRisk
import com.buddy.aios.core.domain.agent.GoalType
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.entity.Task
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyzes incoming user requests to determine the goal type and execution strategy.
 */
@Singleton
class GoalAnalyzer @Inject constructor() {

    companion object {
        private const val TAG = "GoalAnalyzer"
    }

    fun analyze(
        userRequest: String,
        relevantMemories: List<Memory> = emptyList(),
        activeTasks: List<Task> = emptyList(),
    ): GoalAnalysis {
        val raw = userRequest.trim()
        val lower = raw.lowercase()

        AppLogger.d(TAG, "Analyzing request: '$raw'")

        // 1. High-Risk / Bulk Operations Gate
        if (isBulkDeletion(lower)) {
            val description = if (lower.contains("memories")) {
                "permanently delete all stored memories"
            } else {
                "permanently delete all your tasks"
            }
            return GoalAnalysis.HighRiskConfirmation(
                originalRequest = raw,
                goalType = GoalType.COMMAND,
                actionDescription = description,
                toolName = if (lower.contains("memories")) "DeleteMemory" else "DeleteTask",
                arguments = mapOf("bulk" to "true"),
                riskLevel = ActionRisk.HIGH,
            )
        }

        // 2. Ambiguous Requests (e.g. "Delete it", "Complete it")
        if (isAmbiguous(lower)) {
            return GoalAnalysis.AmbiguousRequest(
                originalRequest = raw,
                clarificationQuestion = "Could you specify what task or item you'd like me to update?",
            )
        }

        // 2.5 Follow-up Requests (e.g. "Make it shorter", "Why is it useful?")
        if (isFollowUp(lower)) {
            val modificationType = when {
                lower.contains("shorter") || lower.contains("brief") || lower.contains("compact") -> "SHORTEN"
                lower.contains("longer") || lower.contains("detail") -> "EXPAND"
                lower.contains("why") || lower.contains("how") -> "EXPLAIN_REASONING"
                else -> "MODIFY"
            }
            return GoalAnalysis.FollowUp(
                originalRequest = raw,
                targetReference = "previous_context",
                modificationType = modificationType,
            )
        }

        // 3. Single-Step Task/Reminder Creation
        if (isTaskCreation(lower)) {
            val (title, dueTimestamp, recurrenceRule) = extractTaskDetails(raw)
            return GoalAnalysis.SingleStepAction(
                originalRequest = raw,
                goalType = GoalType.COMMAND,
                toolName = "CreateTask",
                arguments = buildMap {
                    put("title", title)
                    if (dueTimestamp != null) put("dueTimestamp", dueTimestamp.toString())
                    if (recurrenceRule != null) put("recurrenceRule", recurrenceRule)
                },
                riskLevel = ActionRisk.LOW,
            )
        }

        // 4. Single-Step Memory Saving
        if (isMemorySaving(lower)) {
            val memoryContent = extractMemoryContent(raw)
            return GoalAnalysis.SingleStepAction(
                originalRequest = raw,
                goalType = GoalType.COMMAND,
                toolName = "SaveMemory",
                arguments = mapOf("content" to memoryContent),
                riskLevel = ActionRisk.LOW,
            )
        }

        // 5. Complete / Delete Task Single Action
        if (lower.startsWith("complete ") || lower.startsWith("finish ") || lower.startsWith("mark done") || lower.startsWith("mark my ")) {
            val taskTitle = raw.replace(Regex("(?i)^(complete|finish|mark done|mark my)\\s+"), "")
                .replace(Regex("(?i)\\s+(complete|done)$"), "")
                .replace(Regex("(?i)^task\\s+"), "")
                .replace(Regex("(?i)^reminder\\s+"), "")
                .trim()
            return GoalAnalysis.SingleStepAction(
                originalRequest = raw,
                goalType = GoalType.COMMAND,
                toolName = "CompleteTask",
                arguments = mapOf("title" to taskTitle),
                riskLevel = ActionRisk.MEDIUM,
            )
        }

        if (lower.startsWith("delete task ") || lower.startsWith("remove task ") || lower.startsWith("cancel my ") || lower.startsWith("cancel reminder ")) {
            val taskTitle = raw.replace(Regex("(?i)^(delete task|remove task|cancel my|cancel reminder|delete my)\\s+"), "")
                .replace(Regex("(?i)^reminder\\s+"), "")
                .replace(Regex("(?i)^task\\s+"), "")
                .trim()
            return GoalAnalysis.SingleStepAction(
                originalRequest = raw,
                goalType = GoalType.COMMAND,
                toolName = "DeleteTask",
                arguments = mapOf("title" to taskTitle),
                riskLevel = ActionRisk.MEDIUM,
            )
        }

        // 6. Diagnostic Problem Workflow
        if (isDiagnosticProblem(lower)) {
            val problemArea = when {
                lower.contains("docker") -> "Docker environment"
                lower.contains("spring") || lower.contains("boot") -> "Spring Boot application"
                lower.contains("database") || lower.contains("sql") -> "Database configuration"
                else -> "Application / Code execution"
            }
            val missingInfo = if (!lower.contains("log") && !lower.contains("stack trace") && !lower.contains("error message")) {
                "stack trace or error logs"
            } else null

            return GoalAnalysis.DiagnosticProblem(
                originalRequest = raw,
                problemArea = problemArea,
                missingInfoNeeded = missingInfo,
            )
        }

        // 7. Multi-Step Goal
        if (isMultiStepGoal(lower)) {
            val normalized = when {
                lower.contains("interview") -> "Prepare for interview"
                lower.contains("study") || lower.contains("learn") -> "Comprehensive learning plan"
                lower.contains("organize") || lower.contains("schedule my day") -> "Daily task organization"
                else -> raw
            }
            return GoalAnalysis.MultiStepGoal(
                originalRequest = raw,
                normalizedGoal = normalized,
                priority = 1,
            )
        }

        // 8. Conversational Request
        if (isConversational(lower)) {
            return GoalAnalysis.Conversational(originalRequest = raw)
        }

        // 9. Default: Simple Informational Question
        val topic = raw.take(60)
        return GoalAnalysis.SimpleQuestion(originalRequest = raw, topic = topic)
    }

    // ── Helper Heuristics ────────────────────────────────────────────────────

    private fun isBulkDeletion(lower: String): Boolean {
        return lower.contains(Regex("(delete all|remove all|clear all|wipe all|purge all)")) &&
                (lower.contains("task") || lower.contains("memor") || lower.contains("data") || lower.contains("everything"))
    }

    private fun isAmbiguous(lower: String): Boolean {
        val trimmed = lower.trim()
        return (trimmed == "delete it" || trimmed == "remove it" || trimmed == "do it" || trimmed == "complete it" || trimmed == "change it")
    }

    private fun isFollowUp(lower: String): Boolean {
        return lower.startsWith("make it ") ||
                lower.startsWith("make that ") ||
                lower.startsWith("make the ") ||
                lower.startsWith("shorten ") ||
                lower.contains("make that plan") ||
                lower.contains("why is it") ||
                lower.contains("how is it") ||
                lower.contains("what about tomorrow") ||
                lower.contains("tell me more about it")
    }

    private fun isTaskCreation(lower: String): Boolean {
        return lower.startsWith("remind me") ||
                lower.startsWith("reminder") ||
                lower.startsWith("create task") ||
                lower.startsWith("add task") ||
                lower.startsWith("set a reminder") ||
                lower.startsWith("don't forget") ||
                lower.contains(Regex("(remind me to|set a reminder to|set a reminder for|add task to)"))
    }

    private fun isMemorySaving(lower: String): Boolean {
        return lower.startsWith("remember that") ||
                lower.startsWith("remember i") ||
                lower.startsWith("save memory") ||
                lower.startsWith("note that") ||
                lower.startsWith("save that")
    }

    private fun isDiagnosticProblem(lower: String): Boolean {
        return lower.contains(Regex("(crash|error|exception|fail|bug|broken|not working|won't start|doesn't start|fails on startup|stack trace)"))
    }

    private fun isMultiStepGoal(lower: String): Boolean {
        return lower.contains(Regex("(help me prepare|prepare for|build a study plan|study plan for|organize my day|guide me through|roadmap for|help me become)"))
    }

    private fun isConversational(lower: String): Boolean {
        return lower.length < 35 && lower.contains(Regex("^(hello|hi|hey|good morning|good evening|how are you|i'm tired|i feel|who are you)"))
    }

    data class ExtractedTaskDetails(
        val title: String,
        val dueTimestamp: Long?,
        val recurrenceRule: String?,
    )

    private fun extractTaskDetails(raw: String): ExtractedTaskDetails {
        var text = raw.replace(Regex("(?i)^(remind me to|remind me|set a reminder to|set a reminder for|create task|add task|set a reminder)\\s+"), "").trim()
        val now = System.currentTimeMillis()
        var dueTimestamp: Long? = null
        var recurrenceRule: String? = null

        // 1. Recurrence check (e.g. "every day at 7 PM", "daily")
        if (text.lowercase().contains("every day") || text.lowercase().contains("daily")) {
            recurrenceRule = "DAILY"
            text = text.replace(Regex("(?i)\\s*(every day|daily)"), "").trim()
        } else if (text.lowercase().contains("every week") || text.lowercase().contains("weekly")) {
            recurrenceRule = "WEEKLY"
            text = text.replace(Regex("(?i)\\s*(every week|weekly)"), "").trim()
        }

        // 2. Relative time check: "in X minute(s)" / "in X min(s)" / "in X hour(s)"
        val minuteMatch = Regex("(?i)in\\s+(\\d+)\\s*(minute|minutes|min|mins)").find(text)
        if (minuteMatch != null) {
            val mins = minuteMatch.groupValues[1].toLongOrNull() ?: 1L
            dueTimestamp = now + (mins * 60 * 1000L)
            text = text.replace(minuteMatch.value, "").trim()
        }

        val hourMatch = Regex("(?i)in\\s+(\\d+)\\s*(hour|hours|hr|hrs)").find(text)
        if (dueTimestamp == null && hourMatch != null) {
            val hrs = hourMatch.groupValues[1].toLongOrNull() ?: 1L
            dueTimestamp = now + (hrs * 3600 * 1000L)
            text = text.replace(hourMatch.value, "").trim()
        }

        // 3. Absolute time check: "at X PM" / "at X AM" / "tomorrow at X"
        if (dueTimestamp == null) {
            val timeMatch = Regex("(?i)(tomorrow\\s+at|at)\\s+(\\d{1,2})\\s*(:\ud83d\udd50\\d{2})?\\s*(pm|am)?").find(text)
            val simpleTimeMatch = Regex("(?i)(tomorrow\\s+at|at)\\s+(\\d{1,2})\\s*(pm|am)").find(text)
            val matched = simpleTimeMatch ?: timeMatch

            if (matched != null) {
                val isTomorrow = matched.value.lowercase().contains("tomorrow")
                val hourNum = matched.groupValues[2].toIntOrNull() ?: 9
                val amPm = matched.groupValues.lastOrNull()?.lowercase() ?: "pm"

                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    if (isTomorrow) add(Calendar.DAY_OF_YEAR, 1)

                    var targetHour = hourNum
                    if (amPm == "pm" && targetHour < 12) targetHour += 12
                    if (amPm == "am" && targetHour == 12) targetHour = 0

                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)

                    // If target time today has already passed, bump to tomorrow
                    if (!isTomorrow && timeInMillis <= now) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                dueTimestamp = cal.timeInMillis
                text = text.replace(matched.value, "").trim()
            }
        }

        // Clean trailing prepositions ("to", "at", "for")
        text = text.replace(Regex("(?i)^(to|for|at)\\s+"), "")
            .replace(Regex("(?i)\\s+(to|at|for)$"), "")
            .trim()

        val title = text.replaceFirstChar { it.uppercase() }.ifBlank { "New Reminder" }
        return ExtractedTaskDetails(title = title, dueTimestamp = dueTimestamp, recurrenceRule = recurrenceRule)
    }

    private fun extractMemoryContent(raw: String): String {
        return raw.replace(Regex("(?i)^(remember that|remember i|save memory|note that|save that)\\s+"), "")
            .trim()
            .replaceFirstChar { it.uppercase() }
    }
}
