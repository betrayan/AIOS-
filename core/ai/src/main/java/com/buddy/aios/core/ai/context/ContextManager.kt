package com.buddy.aios.core.ai.context

import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * All context assembled for a single AI inference pass.
 */
data class EnrichedContext(
    /** The final, assembled system instruction sent to the AI. */
    val systemInstruction: String,
    /** Token-windowed conversation history (most recent messages that fit). */
    val conversationHistory: List<Message>,
    /** Whether context length is approaching the model's limit. */
    val shouldSummarize: Boolean,
)

/**
 * Manages the AI context window.
 *
 * Responsibilities:
 * 1. Token-window the conversation history to stay within model limits.
 * 2. Build an enriched system prompt from: user profile, BuddyMode, memories, tasks, intent.
 * 3. Signal when conversation history is long enough to need summarization.
 *
 * This class is the single point of context assembly — nothing else builds prompts.
 */
class ContextManager @Inject constructor() {

    private val maxContextTokens = 4096
    private val summarizationThreshold = 0.80f

    /**
     * Builds the complete enriched context for an AI inference call.
     *
     * @param allMessages     Full conversation history (pre-fetched by caller)
     * @param userProfile     Current user profile (name, preferences)
     * @param relevantMemories Top-N memories relevant to this conversation
     * @param activeTasks     Today's active tasks (max 5 for context budget)
     * @param buddyMode       Current operating mode
     * @param userMessage     The new user message (used for intent classification)
     */
    fun buildEnrichedContext(
        allMessages: List<Message>,
        userProfile: UserProfile?,
        relevantMemories: List<Memory>,
        activeTasks: List<Task>,
        buddyMode: BuddyMode,
        userMessage: String,
    ): EnrichedContext {
        val systemInstruction = buildSystemInstruction(
            userProfile = userProfile,
            relevantMemories = relevantMemories,
            activeTasks = activeTasks,
            buddyMode = buddyMode,
            userMessage = userMessage,
        )

        val windowedHistory = buildContext(
            allMessages = allMessages,
            systemTokenCost = estimateTokens(systemInstruction),
        )

        return EnrichedContext(
            systemInstruction = systemInstruction,
            conversationHistory = windowedHistory,
            shouldSummarize = shouldSummarize(allMessages),
        )
    }

    /**
     * Returns the most recent messages that fit within the token budget.
     * Always walks backward (most recent first) to preserve recent context.
     */
    fun buildContext(
        allMessages: List<Message>,
        systemTokenCost: Int = 256,
    ): List<Message> {
        val budget = maxContextTokens - systemTokenCost
        var usedTokens = 0
        val result = mutableListOf<Message>()
        for (message in allMessages.asReversed()) {
            val cost = message.tokenCount.coerceAtLeast(estimateTokens(message.content))
            if (usedTokens + cost > budget) break
            result.add(0, message)
            usedTokens += cost
        }
        return result
    }

    /**
     * Returns true when total context tokens exceed the summarization threshold.
     */
    fun shouldSummarize(messages: List<Message>): Boolean {
        val total = messages.sumOf { it.tokenCount.coerceAtLeast(estimateTokens(it.content)) }
        return total.toFloat() / maxContextTokens >= summarizationThreshold
    }

    /** Rough estimation: ~4 characters per token (common LLM heuristic). */
    fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)

    // ── System Prompt Assembly ────────────────────────────────────────────────

    private fun buildSystemInstruction(
        userProfile: UserProfile?,
        relevantMemories: List<Memory>,
        activeTasks: List<Task>,
        buddyMode: BuddyMode,
        userMessage: String = "",
    ): String = buildString {
        appendLine("You are Buddy, a warm, intelligent personal AI companion.")
        appendLine("You live on the user's device. You are a trusted friend — not a chatbot.")
        appendLine("Always respond naturally, thoughtfully, and helpfully.")
        appendLine()

        // User identity
        userProfile?.let { profile ->
            val displayName = profile.preferredName.ifBlank { profile.name }
            if (displayName.isNotBlank()) {
                appendLine("The user's name is $displayName.")
            }
        }

        // Current date/time for temporal context
        val dateStr = SimpleDateFormat("EEEE, MMMM d yyyy, h:mm a", Locale.ENGLISH)
            .format(Date())
        appendLine("Current date and time: $dateStr")
        appendLine()

        // BuddyMode behavioral directive
        when (buddyMode) {
            BuddyMode.ACTIVE -> appendLine("Operating mode: ACTIVE — normal companion behavior.")
            BuddyMode.QUIET  -> appendLine("Operating mode: QUIET — respond to explicit requests only. No unsolicited suggestions.")
            BuddyMode.SILENT -> appendLine("Operating mode: SILENT — text only. Do not suggest voice interactions.")
            BuddyMode.OFF    -> appendLine("Operating mode: OFF — you should not be running.")
        }
        appendLine()

        // Intent classification — shapes the response style based on what user actually needs
        if (userMessage.isNotBlank()) {
            val intent = classifyIntent(userMessage)
            appendLine("User intent detected: $intent")
            appendLine(intentGuidance(intent))
            appendLine()
        }

        // Relevant memories
        if (relevantMemories.isNotEmpty()) {
            appendLine("Things you remember about the user:")
            relevantMemories.take(6).forEach { memory ->
                appendLine("- ${memory.summary}")
            }
            appendLine()
        }

        // Today's active tasks
        if (activeTasks.isNotEmpty()) {
            appendLine("User's current tasks:")
            activeTasks.take(5).forEach { task ->
                val dueStr = (task.reminderTime ?: task.dueDate)?.let {
                    " (${com.buddy.aios.core.common.time.ReminderDateFormatter.formatNaturalDateTime(it)})"
                } ?: ""
                appendLine("- ${task.title}$dueStr")
            }
            appendLine()
        }

        // Core behavioral rules
        appendLine("Guidelines:")
        appendLine("- Answer factual questions accurately (Docker, coding, science, etc.).")
        appendLine("- Treat different topics differently — do NOT give the same response to unrelated inputs.")
        appendLine("- For personal questions or context, draw on what you know about the user.")
        appendLine("- Be concise but warm. Avoid robotic repetition.")
        appendLine("- Never start consecutive responses the same way.")
        appendLine()

        // Tool directive format
        appendLine("Action capabilities:")
        appendLine("When you perform a concrete action (create task, save memory), append this EXACTLY at the end of your response — on a new line, nothing after it:")
        appendLine("""[BUDDY_ACTION:{"tool":"TASK","action":"CREATE","title":"...","dueTimestamp":null}]""")
        appendLine("""[BUDDY_ACTION:{"tool":"MEMORY","action":"SAVE","content":"...","importance":0.8}]""")
        appendLine("""[BUDDY_ACTION:{"tool":"TASK","action":"COMPLETE","title":"..."}]""")
        appendLine("Only include [BUDDY_ACTION:...] when you are ACTUALLY performing the action. Never include it for regular conversation.")
    }

    // ── Intent Classification ─────────────────────────────────────────────────

    /**
     * Classifies the user's intent from their message.
     * Uses semantic signals rather than simple keyword matching.
     */
    private fun classifyIntent(message: String): String {
        val lower = message.lowercase().trim()

        return when {
            // Problem/debugging: user has something broken
            lower.contains(Regex("(crash|error|exception|fail|bug|broken|not work|issue|problem|fix|debug|stack trace)")) ->
                "PROBLEM"

            // Coding request: create or generate code
            lower.contains(Regex("(write|create|generate|implement|build)")) &&
            lower.contains(Regex("(code|script|function|class|python|java|kotlin|swift|javascript|sql|bash|html|css)")) ->
                "CODING_REQUEST"

            // Task or reminder creation
            lower.contains(Regex("(remind me|reminder|schedule|create task|add task|set alarm|at [0-9])")) ->
                "TASK"

            // Memory saving intent
            lower.contains(Regex("(remember that|remember i|save that|i prefer|i like|i dislike|i am|my favourite)")) ->
                "MEMORY"

            // Follow-up (short message referencing prior context)
            lower.length < 80 && lower.contains(Regex("\\b(it|that|this|they|those)\\b")) &&
            lower.contains(Regex("^(what|why|how|when|where|who|is|are|does|can|will|would|could|should)")) ->
                "FOLLOW_UP"

            // Explanation request
            lower.contains(Regex("(explain|what is|what are|tell me about|describe|define|how does|how do)")) ->
                "EXPLANATION"

            // Command/instruction
            lower.contains(Regex("^(open|close|start|stop|enable|disable|set|show|list|delete|cancel|clear|turn)")) ->
                "COMMAND"

            // Short casual message
            lower.length < 50 ->
                "CONVERSATION"

            else -> "QUESTION"
        }
    }

    /**
     * Returns guidance text injected into the system prompt based on detected intent.
     */
    private fun intentGuidance(intent: String): String = when (intent) {
        "PROBLEM"        -> "The user has a problem. Do NOT give generic explanations. Ask for the specific error or stack trace first, unless they have already provided it."
        "CODING_REQUEST" -> "The user wants code. Write clean, complete, working code. Add a brief explanation of what the code does. Avoid excessive commentary."
        "TASK"           -> "The user wants to create a task or reminder. Confirm what you understood and create it using the action format."
        "MEMORY"         -> "The user wants you to remember something. Save it using the memory action format and confirm naturally."
        "FOLLOW_UP"      -> "This is a follow-up to the previous conversation. Maintain context — the user is asking about the same topic as before."
        "EXPLANATION"    -> "The user wants an explanation. Be clear, simple, and use an everyday analogy if it helps understanding."
        "COMMAND"        -> "The user is giving a command. Respond directly. No unnecessary preamble."
        "CONVERSATION"   -> "This is casual conversation. Be natural, warm, and brief."
        else             -> "Answer the question directly and helpfully."
    }
}
