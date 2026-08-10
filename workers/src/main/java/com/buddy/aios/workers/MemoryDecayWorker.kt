package com.buddy.aios.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IMemoryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Prunes expired and low-importance memories on a daily schedule.
 *
 * Constraints: Device idle preferred (battery impact is low).
 * Schedule: Once daily.
 * Mode Enforcement: Respects [IBuddyModeRepository]. Halts when AI background activity is disabled.
 */
@HiltWorker
class MemoryDecayWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val memoryRepository: IMemoryRepository,
    private val buddyModeRepository: IBuddyModeRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Enforce centralized BuddyMode capability check
        val capabilities = buddyModeRepository.getCapabilities()
        if (!capabilities.allowAiBackgroundProcessing) {
            return Result.success() // Halts cleanly when Buddy is OFF
        }

        return try {
            val pruned = memoryRepository.pruneExpiredMemories()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "MemoryDecayWorker"
        private const val MAX_RETRIES = 3
    }
}
