package com.buddy.aios.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["importance"]),
        Index(value = ["expires_at"]),
        Index(value = ["last_accessed_at"]),
    ],
)
data class MemoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    /** AES-GCM encrypted summary. Decrypted by mapper. */
    @ColumnInfo(name = "summary_encrypted")
    val summaryEncrypted: String,

    @ColumnInfo(name = "source_conversation_id")
    val sourceConversationId: String? = null,

    @ColumnInfo(name = "importance")
    val importance: Float,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,

    @ColumnInfo(name = "tags_json")
    val tagsJson: String = "[]",
)
