package com.buddy.aios.core.domain.entity

/**
 * Domain entity representing a long-term memory unit extracted from conversations.
 * Importance decays over time; [MemoryDecayWorker] prunes low-importance memories.
 */
data class Memory(
    val id: String,
    val userId: String,
    val summary: String,
    val sourceConversationId: String?,
    val importance: Float,          // 0.0f–1.0f
    val createdAt: Long,
    val lastAccessedAt: Long,
    val expiresAt: Long?,
    val tags: List<String> = emptyList(),
)
