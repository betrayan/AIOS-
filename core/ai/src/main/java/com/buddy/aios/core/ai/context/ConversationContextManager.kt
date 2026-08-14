package com.buddy.aios.core.ai.context

import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.entity.MessageRole
import com.buddy.aios.core.domain.entity.Task
import javax.inject.Inject
import javax.inject.Singleton

data class ConversationContext(
    val conversationId: String,
    val currentTopic: String? = null,
    val previousUserIntent: String? = null,
    val previousAssistantIntent: String? = null,
    val recentEntities: List<String> = emptyList(),
    val activeTask: Task? = null,
    val rollingSummary: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
)

/**
 * Tracks multi-turn conversation continuity, topic drift, pronoun resolution, and rolling summaries.
 */
@Singleton
class ConversationContextManager @Inject constructor() {

    private val activeContexts = mutableMapOf<String, ConversationContext>()

    fun getOrCreateContext(conversationId: String): ConversationContext {
        return activeContexts.getOrPut(conversationId) {
            ConversationContext(conversationId = conversationId)
        }
    }

    /**
     * Resolves pronouns and contextual queries relative to previous topics & entities.
     * Example: "Why do people use it?" -> attaches topic "Docker".
     */
    fun resolveContextualQuery(conversationId: String, userMessage: String): String {
        val context = getOrCreateContext(conversationId)
        val topic = context.currentTopic ?: return userMessage
        val lower = userMessage.lowercase().trim()

        // Check if query relies on prior topic pronoun
        val reliesOnPronoun = lower.contains(Regex("\\b(it|that|this|they|those|the tool|the project|the app)\\b")) ||
                lower.startsWith("why") || lower.startsWith("how") || lower.startsWith("give me an example")

        return if (reliesOnPronoun && !lower.contains(topic.lowercase())) {
            "$userMessage (Context topic: $topic)"
        } else {
            userMessage
        }
    }

    /**
     * Updates topic, active task, and rolling summary after an inference pass.
     */
    fun updateContextAfterTurn(
        conversationId: String,
        userMessage: String,
        assistantResponse: String,
        activeTask: Task? = null,
        allMessages: List<Message> = emptyList(),
    ) {
        val current = getOrCreateContext(conversationId)

        // 1. Infer topic if not set or if topic shifted
        val inferredTopic = inferTopic(userMessage, assistantResponse) ?: current.currentTopic

        // 2. Rolling summary if message count >= 10
        val updatedSummary = if (allMessages.size >= 10) {
            val userTurns = allMessages.takeLast(6).filter { it.role == MessageRole.USER }.joinToString("; ") { it.content.take(60) }
            "User & Buddy discussing $inferredTopic. Recent topics: $userTurns."
        } else {
            current.rollingSummary
        }

        val updated = current.copy(
            currentTopic = inferredTopic,
            activeTask = activeTask ?: current.activeTask,
            rollingSummary = updatedSummary,
            lastUpdated = System.currentTimeMillis(),
        )

        activeContexts[conversationId] = updated
    }

    private fun inferTopic(userMessage: String, assistantResponse: String): String? {
        val combined = "$userMessage $assistantResponse".lowercase()
        return when {
            combined.contains("docker") -> "Docker"
            combined.contains("kubernetes") -> "Kubernetes"
            combined.contains("java") -> "Java"
            combined.contains("kotlin") -> "Kotlin"
            combined.contains("python") -> "Python"
            combined.contains("dsa") || combined.contains("algorithm") -> "Data Structures & Algorithms"
            combined.contains("android") -> "Android Development"
            combined.contains("aios") -> "Buddy AI OS"
            else -> null
        }
    }
}
