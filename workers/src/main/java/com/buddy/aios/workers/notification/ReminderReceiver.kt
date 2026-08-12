package com.buddy.aios.workers.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.common.notification.AIOSNotificationManager
import com.buddy.aios.core.data.mapper.toDomain
import com.buddy.aios.core.database.dao.TaskDao
import com.buddy.aios.core.database.entity.TaskEntity
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver triggered by OS AlarmManager when a reminder reaches its scheduled time.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"

        const val ACTION_TRIGGER_REMINDER = "com.buddy.aios.ACTION_TRIGGER_REMINDER"
        const val EXTRA_TASK_ID          = "extra_task_id"
        const val EXTRA_TASK_TITLE       = "extra_task_title"
        const val EXTRA_TASK_DESC        = "extra_task_desc"
        const val EXTRA_NOTIFICATION_ID  = "extra_notification_id"
        const val EXTRA_RECURRENCE_RULE  = "extra_recurrence_rule"
    }

    @Inject
    lateinit var taskDao: TaskDao

    @Inject
    lateinit var buddyModeRepository: IBuddyModeRepository

    @Inject
    lateinit var notificationManager: AIOSNotificationManager

    @Inject
    lateinit var scheduler: IReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER_REMINDER) return

        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "AIOS Reminder"
        val desc = intent.getStringExtra(EXTRA_TASK_DESC) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, taskId.hashCode())
        val recurrenceRule = intent.getStringExtra(EXTRA_RECURRENCE_RULE)

        AppLogger.d(TAG, "Reminder trigger received: id=$taskId title='$title'")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Verify task exists & is not already completed
                val taskEntity = taskDao.getById(taskId)
                if (taskEntity == null) {
                    AppLogger.w(TAG, "Task id=$taskId not found in database — skipping notification")
                    return@launch
                }

                if (taskEntity.isCompleted) {
                    AppLogger.d(TAG, "Task id=$taskId is already completed — skipping notification")
                    return@launch
                }

                val buddyMode = buddyModeRepository.getBuddyMode()
                if (buddyMode == BuddyMode.OFF) {
                    AppLogger.d(TAG, "BuddyMode is OFF — skipping notification for task id=$taskId")
                    return@launch
                }

                // 2. Build Notification Actions (DONE & SNOOZE)
                val mainAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "task")
                    putExtra("task_id", taskId)
                }
                val contentPendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    mainAppIntent ?: Intent(),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // DONE Action Intent
                val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                    action = ReminderActionReceiver.ACTION_COMPLETE_TASK
                    putExtra(EXTRA_TASK_ID, taskId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
                val donePendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId * 10 + 1,
                    doneIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // SNOOZE 10M Action Intent
                val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                    action = ReminderActionReceiver.ACTION_SNOOZE_TASK
                    putExtra(EXTRA_TASK_ID, taskId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(ReminderActionReceiver.EXTRA_SNOOZE_MINUTES, 10)
                }
                val snoozePendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId * 10 + 2,
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notificationBody = if (desc.isNotBlank()) desc else title

                val builder = NotificationCompat.Builder(context, AIOSNotificationManager.CHANNEL_REMINDER)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("AIOS Reminder")
                    .setContentText(notificationBody)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(notificationBody))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent)
                    .addAction(android.R.drawable.checkbox_on_background, "DONE", donePendingIntent)
                    .addAction(android.R.drawable.ic_popup_sync, "SNOOZE (10m)", snoozePendingIntent)

                if (notificationManager.hasNotificationPermission()) {
                    NotificationManagerCompat.from(context).notify(notificationId, builder.build())
                    AppLogger.d(TAG, "Posted reminder notification id=$taskId notificationId=$notificationId")
                } else {
                    AppLogger.w(TAG, "Notification permission denied — could not post notification for task id=$taskId")
                }

                // 3. Handle Recurring Reschedule if applicable
                if (!recurrenceRule.isNullOrEmpty()) {
                    val nextTrigger = when (recurrenceRule.uppercase()) {
                        "DAILY"  -> System.currentTimeMillis() + (24 * 3600 * 1000L)
                        "WEEKLY" -> System.currentTimeMillis() + (7 * 24 * 3600 * 1000L)
                        else -> 0L
                    }
                    if (nextTrigger > 0L) {
                        taskDao.updateReminderTime(taskId, nextTrigger, "PENDING")
                        val updatedEntity: TaskEntity? = taskDao.getById(taskId)
                        if (updatedEntity != null) {
                            val updatedDomain = updatedEntity.toDomain()
                            scheduler.schedule(updatedDomain)
                            AppLogger.d(TAG, "Rescheduled recurring task id=$taskId for nextTrigger=$nextTrigger")
                        }
                    }
                }

            } catch (e: Exception) {
                AppLogger.e(TAG, "Error handling reminder trigger for task id=$taskId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
