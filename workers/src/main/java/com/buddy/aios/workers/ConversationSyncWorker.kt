package com.buddy.aios.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IConversationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodically syncs local conversations to the cloud.
 *
 * Constraints: Network required.
 * Schedule: Periodic every 15 minutes, with expedited run on network restore.
 * Mode Enforcement: Respects [IBuddyModeRepository]. Halts when AI background activity is disabled.
 */
@HiltWorker
class ConversationSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val conversationRepository: IConversationRepository,
    private val buddyModeRepository: IBuddyModeRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Enforce centralized BuddyMode capability check
        val capabilities = buddyModeRepository.getCapabilities()
        if (!capabilities.allowAiBackgroundProcessing) {
            return Result.success() // Halts cleanly when Buddy is OFF
        }

        return try {
            val conversationId = inputData.getString(KEY_CONVERSATION_ID)
            if (conversationId != null) {
                conversationRepository.syncConversation(conversationId)
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val WORK_NAME_PERIODIC = "ConversationSyncWorker_periodic"
        private const val MAX_RETRIES = 3
    }
}
