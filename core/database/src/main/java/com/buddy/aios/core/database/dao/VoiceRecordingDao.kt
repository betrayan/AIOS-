package com.buddy.aios.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.buddy.aios.core.database.entity.VoiceRecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceRecordingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: VoiceRecordingEntity)

    @Query("SELECT * FROM voice_recordings ORDER BY created_at DESC")
    fun observeAll(): Flow<List<VoiceRecordingEntity>>

    @Query("SELECT * FROM voice_recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VoiceRecordingEntity?

    @Query("DELETE FROM voice_recordings WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM voice_recordings")
    suspend fun count(): Int

    @Query("SELECT SUM(size_bytes) FROM voice_recordings")
    suspend fun totalSizeBytes(): Long?

    @Query("DELETE FROM voice_recordings")
    suspend fun deleteAll()
}
