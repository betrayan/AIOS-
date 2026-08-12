package com.buddy.aios.workers.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.repository.IReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OS-level AlarmManager Reminder Scheduler.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : IReminderScheduler {
    companion object {
        private const val TAG = "ReminderScheduler"
    }

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    override fun schedule(task: Task): Boolean {
        val triggerTime = task.reminderTime ?: task.dueDate ?: return false

        if (triggerTime <= System.currentTimeMillis()) {
            AppLogger.w(TAG, "Skipping scheduling for expired task id=${task.id} (time passed)")
            return false
        }

        val manager = alarmManager
        if (manager == null) {
            AppLogger.e(TAG, "AlarmManager service is null")
            return false
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TRIGGER_REMINDER
            putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id)
            putExtra(ReminderReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(ReminderReceiver.EXTRA_TASK_DESC, task.description)
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, task.notificationId)
            putExtra(ReminderReceiver.EXTRA_RECURRENCE_RULE, task.recurrenceRule)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val localTimeFormatted = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone(task.timezone)
        }.format(Date(triggerTime))

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                AppLogger.d(TAG, "Scheduled EXACT reminder id=${task.id} for $localTimeFormatted (${task.timezone})")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                AppLogger.d(TAG, "Scheduled INEXACT reminder id=${task.id} for $localTimeFormatted (${task.timezone})")
            } else {
                manager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                AppLogger.d(TAG, "Scheduled legacy reminder id=${task.id} for $localTimeFormatted")
            }
            true
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "SecurityException scheduling exact alarm for task id=${task.id}", e)
            false
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to schedule alarm for task id=${task.id}", e)
            false
        }
    }

    override fun cancel(taskId: String, notificationId: Int) {
        val manager = alarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            manager.cancel(pendingIntent)
            pendingIntent.cancel()
            AppLogger.d(TAG, "Cancelled scheduled alarm id=$taskId notificationId=$notificationId")
        }
    }

    override fun cancel(task: Task) {
        cancel(task.id, task.notificationId)
    }
}
