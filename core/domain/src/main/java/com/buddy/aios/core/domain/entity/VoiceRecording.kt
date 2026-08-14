package com.buddy.aios.core.domain.entity

/**
 * Where a saved voice recording file lives on the device.
 */
enum class StorageLocation {
    /** App-private storage (filesDir). No extra permission required. Default. */
    PRIVATE,

    /** Shared device storage via MediaStore. Visible to other apps with permission. */
    DEVICE,
}

/**
 * Domain model for a saved voice recording.
 * Only metadata is stored here — the actual audio lives on disk at [fileUri].
 */
data class VoiceRecording(
    val id: String,
    val fileName: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val storageLocation: StorageLocation,
    /** Absolute file path (PRIVATE) or content URI string (DEVICE / MediaStore). */
    val fileUri: String,
)
