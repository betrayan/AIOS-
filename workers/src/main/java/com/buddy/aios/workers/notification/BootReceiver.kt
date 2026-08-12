package com.buddy.aios.workers.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.data.mapper.toDomain
import com.buddy.aios.core.database.dao.TaskDao
import com.buddy.aios.core.domain.repository.IReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver for BOOT_COMPLETED and MY_PACKAGE_REPLACED events.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    @Inject
    lateinit var taskDao: TaskDao

    @Inject
    lateinit var scheduler: IReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        AppLogger.d(TAG, "Device boot / package update completed — restoring pending reminder alarms")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val pendingEntities = taskDao.getPendingReminders()

                var rescheduledCount = 0
                pendingEntities.forEach { entity ->
                    val reminderTime = entity.reminderTime ?: 0L
                    if (!entity.isCompleted && reminderTime > now) {
                        val domainTask = entity.toDomain()
                        val success = scheduler.schedule(domainTask)
                        if (success) rescheduledCount++
                    }
                }
                AppLogger.d(TAG, "Restored and rescheduled $rescheduledCount pending reminder(s) after reboot")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to reschedule reminders after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
