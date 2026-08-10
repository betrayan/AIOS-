package com.buddy.aios.core.data.mapper

import com.buddy.aios.core.database.entity.MessageEntity
import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.entity.MessageRole
import com.buddy.aios.core.security.EncryptionService

fun MessageEntity.toDomain(encryptionService: EncryptionService): Message {
    return Message(
        id = id,
        conversationId = conversationId,
        role = when (role) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            else -> MessageRole.SYSTEM
        },
        content = encryptionService.decrypt(contentEncrypted),
        timestamp = timestamp,
        tokenCount = tokenCount,
        isMemoryAnchor = isMemoryAnchor,
    )
}

fun Message.toEntity(encryptionService: EncryptionService): MessageEntity {
    val encryptedContent = encryptionService.encrypt(content)
    return MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role.value,
        contentEncrypted = encryptedContent,
        contentHash = content.hashCode().toString(),
        timestamp = timestamp,
        tokenCount = tokenCount,
        isMemoryAnchor = isMemoryAnchor,
    )
}
