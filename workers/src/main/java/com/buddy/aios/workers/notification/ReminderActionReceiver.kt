package com.buddy.aios.workers.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.data.mapper.toDomain
import com.buddy.aios.core.database.dao.TaskDao
import com.buddy.aios.core.domain.repository.IReminderEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles notification action buttons: DONE (complete) and SNOOZE (snooze reminder).
 */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderActionReceiver"

        const val ACTION_COMPLETE_TASK = "com.buddy.aios.ACTION_COMPLETE_TASK"
        const val ACTION_SNOOZE_TASK   = "com.buddy.aios.ACTION_SNOOZE_TASK"

        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    }

    @Inject
    lateinit var reminderEngine: IReminderEngine

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderReceiver.EXTRA_TASK_ID) ?: return
        val notificationId = intent.getIntExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, taskId.hashCode())

        // Immediately cancel displayed notification
        NotificationManagerCompat.from(context).cancel(notificationId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE_TASK -> {
                        AppLogger.d(TAG, "Executing DONE action for task id=$taskId")
                        reminderEngine.completeReminder(taskId)
                    }

                    ACTION_SNOOZE_TASK -> {
                        val snoozeMins = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
                        AppLogger.d(TAG, "Executing SNOOZE action ($snoozeMins mins) for task id=$taskId")
                        reminderEngine.snoozeReminder(taskId, snoozeMins)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error executing reminder action for task id=$taskId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
