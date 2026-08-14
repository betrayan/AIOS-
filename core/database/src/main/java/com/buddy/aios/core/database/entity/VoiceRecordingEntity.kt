package com.buddy.aios.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for voice recording metadata.
 * The actual audio file is stored on disk; only its URI/path is stored here.
 * Raw audio is NEVER stored as a BLOB in the database.
 */
@Entity(tableName = "voice_recordings")
data class VoiceRecordingEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "title")
    val title: String,

    /** Unix epoch millis when recording was created. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** Duration in milliseconds. */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    /** File size in bytes. */
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,

    /** "PRIVATE" or "DEVICE" — maps to StorageLocation enum. */
    @ColumnInfo(name = "storage_location")
    val storageLocation: String,

    /** Absolute file path (private storage) or content URI string (MediaStore). */
    @ColumnInfo(name = "file_uri")
    val fileUri: String,
)
