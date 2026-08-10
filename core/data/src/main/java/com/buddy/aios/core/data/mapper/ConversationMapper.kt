package com.buddy.aios.core.data.mapper

import com.buddy.aios.core.database.entity.ConversationEntity
import com.buddy.aios.core.domain.entity.Conversation

fun ConversationEntity.toDomain(): Conversation {
    return Conversation(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        personaId = personaId,
        isArchived = isArchived,
        summaryHash = summaryHash,
        syncedAt = syncedAt,
        messageCount = messageCount,
    )
}

fun Conversation.toEntity(): ConversationEntity {
    return ConversationEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        personaId = personaId,
        isArchived = isArchived,
        summaryHash = summaryHash,
        syncedAt = syncedAt,
        messageCount = messageCount,
    )
}
