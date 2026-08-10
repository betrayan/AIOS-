package com.buddy.aios.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,   // Delete messages when conversation is deleted
        )
    ],
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["timestamp"]),
    ],
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    @ColumnInfo(name = "role")
    val role: String,    // "user" | "assistant" | "system"

    /** AES-GCM encrypted content. Decrypted by mapper before returning to domain. */
    @ColumnInfo(name = "content_encrypted")
    val contentEncrypted: String,

    @ColumnInfo(name = "content_hash")
    val contentHash: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "token_count")
    val tokenCount: Int = 0,

    @ColumnInfo(name = "is_memory_anchor")
    val isMemoryAnchor: Boolean = false,

    @ColumnInfo(name = "metadata_json")
    val metadataJson: String = "{}",
)
