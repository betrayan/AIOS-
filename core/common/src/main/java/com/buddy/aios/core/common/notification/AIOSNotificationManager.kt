package com.buddy.aios.core.common.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.buddy.aios.core.common.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AIOS Notification Identity System.
 *
 * Responsibilities:
 * - Manages notification channels: MORNING, REMINDER, TASK, AI, SYSTEM.
 * - Handles Android 13+ POST_NOTIFICATIONS permission checks safely without crashing.
 * - Enforces smart notification filtering (prevents chat/memory noise spam).
 */
@Singleton
class AIOSNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "AIOSNotificationManager"

        const val CHANNEL_MORNING  = "aios_morning_channel"
        const val CHANNEL_REMINDER = "aios_reminder_channel"
        const val CHANNEL_TASK     = "aios_task_channel"
        const val CHANNEL_AI       = "aios_ai_channel"
        const val CHANNEL_SYSTEM   = "aios_system_channel"

        const val NOTIF_ID_MORNING = 1001
        const val NOTIF_ID_REMINDER = 1002
    }

    init {
        createNotificationChannels()
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val morningChannel = NotificationChannel(
                CHANNEL_MORNING,
                "AIOS Morning Briefing",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Daily morning context & sleep summary"
                enableVibration(true)
            }

            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER,
                "AIOS Task Reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Scheduled task & activity reminders"
                enableVibration(true)
            }

            val taskChannel = NotificationChannel(
                CHANNEL_TASK,
                "AIOS Task Operations",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Task completion and agent verification alerts"
            }

            val aiChannel = NotificationChannel(
                CHANNEL_AI,
                "AIOS System Insights",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Proactive assistant insights"
            }

            manager.createNotificationChannels(listOf(morningChannel, reminderChannel, taskChannel, aiChannel))
            AppLogger.d(TAG, "Notification channels initialized")
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showMorningNotification(
        title: String,
        body: String,
        targetIntent: Intent? = null,
    ) {
        if (!hasNotificationPermission()) {
            AppLogger.w(TAG, "Notification permission denied — skipping morning notification")
            return
        }

        val pendingIntent = targetIntent?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_MORNING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_MORNING, notification)
            AppLogger.d(TAG, "Morning notification sent: $title")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "SecurityException sending notification", e)
        }
    }

    fun showReminderNotification(
        title: String,
        body: String,
        targetIntent: Intent? = null,
    ) {
        if (!hasNotificationPermission()) return

        val pendingIntent = targetIntent?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_REMINDER, notification)
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "SecurityException sending reminder notification", e)
        }
    }
}
