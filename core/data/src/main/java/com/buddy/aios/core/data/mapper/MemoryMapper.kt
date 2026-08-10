package com.buddy.aios.core.data.mapper

import com.buddy.aios.core.database.entity.MemoryEntity
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.security.EncryptionService

fun MemoryEntity.toDomain(encryptionService: EncryptionService): Memory {
    return Memory(
        id = id,
        userId = userId,
        summary = encryptionService.decrypt(summaryEncrypted),
        sourceConversationId = sourceConversationId,
        importance = importance,
        createdAt = createdAt,
        lastAccessedAt = lastAccessedAt,
        expiresAt = expiresAt,
    )
}

fun Memory.toEntity(encryptionService: EncryptionService): MemoryEntity {
    return MemoryEntity(
        id = id,
        userId = userId,
        summaryEncrypted = encryptionService.encrypt(summary),
        sourceConversationId = sourceConversationId,
        importance = importance,
        createdAt = createdAt,
        lastAccessedAt = lastAccessedAt,
        expiresAt = expiresAt,
    )
}
