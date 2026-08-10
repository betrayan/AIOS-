package com.buddy.aios.core.domain.entity

/**
 * Domain entity representing a chat conversation.
 * Pure Kotlin — zero Android imports.
 */
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val personaId: String,
    val isArchived: Boolean = false,
    val summaryHash: String? = null,
    val syncedAt: Long? = null,
    val messageCount: Int = 0,
)
