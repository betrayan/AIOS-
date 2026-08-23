package com.buddy.aios.workers.notification

import android.annotation.SuppressLint
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
import com.buddy.aios.core.domain.repository.IReminderEngine
import com.buddy.aios.core.domain.repository.IReminderScheduler
import com.buddy.aios.core.common.voice.IVoiceOutputManager
import com.buddy.aios.core.ui.island.AIOSIslandState
import com.buddy.aios.core.ui.island.AIOSIslandStateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * BroadcastReceiver triggered by OS AlarmManager when a reminder reaches its scheduled time.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"

        const val ACTION_TRIGGER_REMINDER = "com.buddy.aios.ACTION_TRIGGER_REMINDER"
        const val ACTION_SCHEDULE_TEST_REMINDER = "com.buddy.aios.ACTION_SCHEDULE_TEST_REMINDER"
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

    @Inject
    lateinit var reminderEngine: IReminderEngine

    @Inject
    lateinit var ttsManager: IVoiceOutputManager

    @Inject
    lateinit var islandStateManager: AIOSIslandStateManager

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SCHEDULE_TEST_REMINDER) {
            val delaySecs = intent.getIntExtra("delay_seconds", 120)
            val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: "test-task-${System.currentTimeMillis()}"
            val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Test AIOS Live Reminder"
            val now = System.currentTimeMillis()
            val triggerTime = now + (delaySecs * 1000L)
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, taskId.hashCode())

            val task = TaskEntity(
                id = taskId,
                title = title,
                description = "Testing exact AIOS reminder reliability",
                isCompleted = false,
                createdAt = now,
                dueDate = triggerTime,
                reminderTime = triggerTime,
                priority = "MEDIUM",
                tagsJson = "[]",
                isReminder = true,
                notificationId = notificationId,
                timezone = "Asia/Kolkata",
                status = "PENDING",
                deliveryState = "SCHEDULED",
                voiceEnabled = true,
                notificationEnabled = true,
                morningEligible = true,
            )

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    taskDao.insert(task)
                    val scheduled = scheduler.schedule(task.toDomain())
                    AppLogger.d(TAG, "Test reminder scheduled via broadcast: id=$taskId triggerTime=$triggerTime scheduled=$scheduled")
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to schedule test reminder", e)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

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
                val handled = reminderEngine.handleReminderTriggered(taskId, notificationId)
                if (!handled) {
                    AppLogger.d(TAG, "Reminder engine ignored trigger for task id=$taskId")
                    return@launch
                }

                val taskEntity = taskDao.getById(taskId) ?: return@launch
                val task = taskEntity.toDomain()

                // Build Notification Actions (DONE & SNOOZE)
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
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent)
                    .addAction(android.R.drawable.checkbox_on_background, "DONE", donePendingIntent)
                    .addAction(android.R.drawable.ic_popup_sync, "SNOOZE (10m)", snoozePendingIntent)

                withContext(Dispatchers.Main) {
                    islandStateManager.show(
                        state = AIOSIslandState.REMINDER,
                        message = "🔔 ${task.title}",
                        autoDismissMs = 4000L,
                    )
                }

                if (notificationManager.hasNotificationPermission()) {
                    NotificationManagerCompat.from(context).notify(notificationId, builder.build())
                    AppLogger.d(TAG, "Posted reminder notification id=$taskId notificationId=$notificationId")
                } else {
                    AppLogger.w(TAG, "Notification permission denied — could not post notification for task id=$taskId")
                }

                // Voice Reminder Delivery (if enabled and BuddyMode allows)
                val buddyMode = buddyModeRepository.getBuddyMode()
                if (task.voiceEnabled && (buddyMode == BuddyMode.ACTIVE || buddyMode == BuddyMode.QUIET)) {
                    val reminderTimestamp = task.reminderTime ?: task.dueDate ?: System.currentTimeMillis()
                    val taskTz = task.timezone.ifBlank { TimeZone.getDefault().id }
                    val timeString = SimpleDateFormat("h:mm a", Locale.ENGLISH).apply {
                        timeZone = TimeZone.getTimeZone(taskTz)
                    }.format(Date(reminderTimestamp))

                    val voiceText = "It is $timeString. Your reminder is due: ${task.title}."
                    withContext(Dispatchers.Main) {
                        ttsManager.speak(voiceText)
                    }
                    AppLogger.d(TAG, "Spoken voice reminder triggered for task id=${task.id} (time=$timeString): '$voiceText'")
                }

            } catch (e: Exception) {
                AppLogger.e(TAG, "Error handling reminder trigger for task id=$taskId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
