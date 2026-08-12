package com.buddy.aios.core.domain.entity

/**
 * Domain entity representing a single message within a conversation.
 *
 * The [isComplete] field is a transient streaming flag — populated only on the
 * final streamed assistant chunk and NOT persisted to the database. It signals
 * to the presentation layer that the full response is ready for TTS/notification routing.
 *
 * Tool execution context is communicated via [metadata]:
 * - Key "tool_type"   → e.g. "CREATE_TASK", "SAVE_MEMORY"
 * - Key "tool_result" → "success" or "failure"
 * - Key "tool_label"  → human-readable label e.g. "Reminder set"
 */
data class Message(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val tokenCount: Int = 0,
    val isMemoryAnchor: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    // ── Transient streaming field (not persisted) ───────────────────────────
    val isComplete: Boolean = false,
)

enum class MessageRole(val value: String) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system"),
}
