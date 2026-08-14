package com.buddy.aios.core.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.database.dao.VoiceRecordingDao
import com.buddy.aios.core.database.entity.VoiceRecordingEntity
import com.buddy.aios.core.domain.entity.StorageLocation
import com.buddy.aios.core.domain.entity.VoiceRecording
import com.buddy.aios.core.domain.repository.IVoiceRecordingRepository
import com.buddy.aios.core.domain.repository.VoiceStorageStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRecordingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: VoiceRecordingDao,
) : IVoiceRecordingRepository {

    private val privateDir: File
        get() = File(context.filesDir, "voice-recordings").also { it.mkdirs() }

    private val tempDir: File
        get() = File(context.cacheDir, "voice_temp").also { it.mkdirs() }

    init {
        // On startup: clean up any orphaned temp files from previous sessions
        try {
            tempDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to clean temp dir on startup: ${e.message}")
        }
    }

    override suspend fun saveRecording(
        tempFilePath: String,
        title: String,
        durationMs: Long,
        location: StorageLocation,
    ): VoiceRecording = withContext(Dispatchers.IO) {
        val tempFile = File(tempFilePath)
        require(tempFile.exists()) { "Temp file not found: $tempFilePath" }

        val id = UUID.randomUUID().toString()
        val fileName = "voice_${System.currentTimeMillis()}.m4a"

        val (finalUri, sizeBytes) = when (location) {
            StorageLocation.PRIVATE -> {
                val dest = File(privateDir, fileName)
                copyFile(tempFile, dest)
                tempFile.delete()
                Pair(dest.absolutePath, dest.length())
            }
            StorageLocation.DEVICE -> {
                val uri = saveToMediaStore(tempFile, fileName)
                val size = tempFile.length()
                tempFile.delete()
                Pair(uri.toString(), size)
            }
        }

        val entity = VoiceRecordingEntity(
            id = id,
            fileName = fileName,
            title = title,
            createdAt = System.currentTimeMillis(),
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            storageLocation = location.name,
            fileUri = finalUri,
        )
        dao.insert(entity)
        AppLogger.d(TAG, "Saved recording id=$id location=$location size=$sizeBytes")
        entity.toDomain()
    }

    override fun getRecordings(): Flow<List<VoiceRecording>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getRecording(id: String): VoiceRecording? =
        withContext(Dispatchers.IO) { dao.getById(id)?.toDomain() }

    override suspend fun deleteRecording(id: String): Boolean = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: run {
            AppLogger.w(TAG, "deleteRecording: id=$id not found in DB")
            return@withContext false
        }

        // Delete the physical file
        val fileDeleted = try {
            when (entity.storageLocation) {
                StorageLocation.PRIVATE.name -> File(entity.fileUri).delete()
                StorageLocation.DEVICE.name -> {
                    val uri = Uri.parse(entity.fileUri)
                    context.contentResolver.delete(uri, null, null) > 0
                }
                else -> false
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not delete file for id=$id: ${e.message}")
            false
        }

        dao.deleteById(id)
        AppLogger.d(TAG, "deleteRecording id=$id fileDeleted=$fileDeleted")
        true
    }

    override suspend fun exists(id: String): Boolean = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext false
        when (entity.storageLocation) {
            StorageLocation.PRIVATE.name -> File(entity.fileUri).exists()
            StorageLocation.DEVICE.name -> {
                try {
                    val uri = Uri.parse(entity.fileUri)
                    context.contentResolver.openInputStream(uri)?.use { true } ?: false
                } catch (e: Exception) { false }
            }
            else -> false
        }
    }

    override suspend fun getStorageStats(): VoiceStorageStats = withContext(Dispatchers.IO) {
        VoiceStorageStats(
            count = dao.count(),
            totalBytes = dao.totalSizeBytes() ?: 0L,
        )
    }

    override suspend fun cleanupTempFiles() = withContext(Dispatchers.IO) {
        try {
            tempDir.listFiles()?.forEach { it.delete() }
            AppLogger.d(TAG, "Cleaned voice temp dir")
        } catch (e: Exception) {
            AppLogger.w(TAG, "cleanupTempFiles error: ${e.message}")
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun saveToMediaStore(tempFile: File, fileName: String): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MUSIC}/AIOS/Voice Recordings")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues)
            ?: throw IllegalStateException("MediaStore insert failed")

        resolver.openOutputStream(uri)?.use { output ->
            FileInputStream(tempFile).use { input -> input.copyTo(output) }
        }

        contentValues.clear()
        contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return uri
    }

    private companion object {
        const val TAG = "VoiceRecordingRepo"
    }
}

private fun VoiceRecordingEntity.toDomain() = VoiceRecording(
    id = id,
    fileName = fileName,
    title = title,
    createdAt = createdAt,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    storageLocation = StorageLocation.valueOf(storageLocation),
    fileUri = fileUri,
)
