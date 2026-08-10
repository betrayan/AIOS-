package com.buddy.aios.core.domain.entity

/**
 * Domain entity representing a single message within a conversation.
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
)

enum class MessageRole(val value: String) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system"),
}
