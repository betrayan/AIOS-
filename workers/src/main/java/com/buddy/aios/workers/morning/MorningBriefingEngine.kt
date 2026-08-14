package com.buddy.aios.workers.morning

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.buddy.aios.core.ai.morning.MorningBriefing
import com.buddy.aios.core.ai.morning.MorningContextEngine
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.common.notification.AIOSNotificationManager
import com.buddy.aios.core.database.dao.TaskDao
import com.buddy.aios.core.data.mapper.toDomain
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.repository.IMorningBriefingSettingsRepository
import com.buddy.aios.core.domain.repository.IUserRepository
import com.buddy.aios.core.domain.result.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class MorningBriefingResult(
    val title: String,
    val notificationBody: String,
    val voiceBriefing: String,
    val priorityTasks: List<Task>,
    val estimatedSleepFormatted: String?,
    val isMorningWindowActive: Boolean,
    val briefing: MorningBriefing? = null,
)

/**
 * Single engine orchestrating morning briefings, context relevance evaluation,
 * notification delivery, and natural voice summaries.
 */
@Singleton
class MorningBriefingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val morningContextEngine: MorningContextEngine,
    private val taskDao: TaskDao,
    private val memoryRepository: IMemoryRepository,
    private val userRepository: IUserRepository,
    private val buddyModeRepository: IBuddyModeRepository,
    private val settingsRepository: IMorningBriefingSettingsRepository,
    private val notificationManager: AIOSNotificationManager,
) {
    companion object {
        private const val TAG = "MorningBriefingEngine"
        private const val PREFS_NAME = "aios_morning_prefs"
        private const val KEY_LAST_BRIEFING_DATE = "last_morning_briefing_date"
    }

    suspend fun generateAndDeliverMorningBriefing(forceDebug: Boolean = false): MorningBriefingResult {
        val nowCal = Calendar.getInstance()
        val hour = nowCal.get(Calendar.HOUR_OF_DAY)
        val isMorningWindow = forceDebug || hour in 5..10

        val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())

        val settings = settingsRepository.getSettings()

        // Check if briefing feature is enabled in user settings
        if (!settings.isBriefingEnabled && !forceDebug) {
            AppLogger.d(TAG, "Morning Briefing is disabled in settings")
            return MorningBriefingResult(
                title = "Morning Briefing Disabled",
                notificationBody = "",
                voiceBriefing = "",
                priorityTasks = emptyList(),
                estimatedSleepFormatted = null,
                isMorningWindowActive = isMorningWindow,
            )
        }

        // Check if briefing was already delivered today (unless debug forced)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDeliveredDate = prefs.getString(KEY_LAST_BRIEFING_DATE, "")

        if (!forceDebug && lastDeliveredDate == todayDateStr) {
            AppLogger.d(TAG, "Morning briefing already delivered today ($todayDateStr)")
        }

        val userProfile = (userRepository.getUserProfile() as? Result.Success)?.value
        val allActiveEntities = taskDao.getPendingReminders()
        val allActiveTasks = allActiveEntities.map { it.toDomain() }

        val sortedTasks = sortTasksForMorning(allActiveTasks)
        val memories = (memoryRepository.searchMemories("") as? Result.Success)?.value ?: emptyList()

        val briefing = morningContextEngine.generateMorningBriefing(
            userProfile = userProfile,
            activeTasks = sortedTasks,
            memories = memories,
        )

        val result = MorningBriefingResult(
            title = briefing.notificationTitle,
            notificationBody = briefing.notificationBody,
            voiceBriefing = briefing.spokenBriefing,
            priorityTasks = briefing.importantReminders,
            estimatedSleepFormatted = briefing.sleepSummary,
            isMorningWindowActive = isMorningWindow,
            briefing = briefing,
        )

        // Deliver Notification if BuddyMode permits and not already delivered
        if (lastDeliveredDate != todayDateStr || forceDebug) {
            deliverMorningNotification(todayDateStr, briefing.notificationTitle, briefing.notificationBody)
            prefs.edit().putString(KEY_LAST_BRIEFING_DATE, todayDateStr).apply()
        }

        AppLogger.d(TAG, "Delivered Morning Briefing for $todayDateStr")
        return result
    }

    private fun sortTasksForMorning(tasks: List<Task>): List<Task> {
        val now = System.currentTimeMillis()
        return tasks.sortedWith(
            compareBy<Task> { task ->
                val time = task.reminderTime ?: task.dueDate ?: Long.MAX_VALUE
                when {
                    time < now && !task.isCompleted -> 0 // Overdue
                    task.priority == TaskPriority.HIGH -> 1
                    else -> 2
                }
            }.thenBy { task ->
                task.reminderTime ?: task.dueDate ?: Long.MAX_VALUE
            }
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun deliverMorningNotification(todayDateStr: String, title: String, body: String) {
        val buddyMode = buddyModeRepository.getBuddyMode()
        if (buddyMode == BuddyMode.OFF) {
            AppLogger.d(TAG, "BuddyMode is OFF — skipping morning notification")
            return
        }

        val notificationId = ("morning_$todayDateStr").hashCode()

        val mainAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "home")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            mainAppIntent ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, AIOSNotificationManager.CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        if (notificationManager.hasNotificationPermission()) {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            AppLogger.d(TAG, "Morning notification posted successfully for date=$todayDateStr")
        }
    }
}
