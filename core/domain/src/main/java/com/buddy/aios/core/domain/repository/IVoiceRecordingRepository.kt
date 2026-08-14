package com.buddy.aios.core.domain.repository

import com.buddy.aios.core.domain.entity.StorageLocation
import com.buddy.aios.core.domain.entity.VoiceRecording
import kotlinx.coroutines.flow.Flow

/**
 * Aggregated statistics for the voice recording vault.
 */
data class VoiceStorageStats(
    val count: Int,
    val totalBytes: Long,
)

/**
 * Repository contract for the Voice Recording Vault.
 * Responsible for:
 * - Moving temporary recordings into permanent storage (private or device).
 * - Persisting metadata to Room.
 * - Cleaning up orphaned metadata when audio files are missing.
 * - Providing storage statistics for the Settings screen.
 *
 * Audio files are NEVER stored as BLOBs inside Room.
 * Audio NEVER leaves the device automatically.
 */
interface IVoiceRecordingRepository {

    /**
     * Move a temporary recording from [tempFilePath] into permanent storage,
     * persist metadata to Room, and return the saved [VoiceRecording].
     *
     * @param tempFilePath Absolute path to the temp audio file (cacheDir/voice_temp/).
     * @param title        User-facing title (defaults to date-based label).
     * @param durationMs   Recording duration in milliseconds.
     * @param location     Target permanent storage location.
     */
    suspend fun saveRecording(
        tempFilePath: String,
        title: String,
        durationMs: Long,
        location: StorageLocation,
    ): VoiceRecording

    /** Observe all recordings ordered by [VoiceRecording.createdAt] descending. */
    fun getRecordings(): Flow<List<VoiceRecording>>

    /** Fetch a single recording by its [id], or null if not found. */
    suspend fun getRecording(id: String): VoiceRecording?

    /**
     * Delete a recording: removes the physical audio file AND the Room metadata row.
     * If the file is already missing, removes the stale metadata and returns true.
     *
     * @return true if deletion succeeded (or file was already gone), false on error.
     */
    suspend fun deleteRecording(id: String): Boolean

    /** Check whether a recording's audio file still exists on disk. */
    suspend fun exists(id: String): Boolean

    /** Return aggregate statistics (count + total bytes) for the vault. */
    suspend fun getStorageStats(): VoiceStorageStats

    /**
     * Delete any leftover temp files in cacheDir/voice_temp/ that were not saved.
     * Called on app startup to clean up after interrupted recording sessions.
     */
    suspend fun cleanupTempFiles()
}
