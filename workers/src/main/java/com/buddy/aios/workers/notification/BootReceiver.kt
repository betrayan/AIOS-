package com.buddy.aios.workers.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
 * BroadcastReceiver for BOOT_COMPLETED and MY_PACKAGE_REPLACED events.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    @Inject
    lateinit var reminderEngine: IReminderEngine

    @Inject
    lateinit var morningWishEngine: com.buddy.aios.core.domain.repository.IMorningWishEngine

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isSupportedAction = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED

        if (!isSupportedAction) return

        AppLogger.d(TAG, "System state event received ($action) — restoring pending reminder & morning wish alarms")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val restoredCount = reminderEngine.restoreReminders()
                morningWishEngine.scheduleMorningWish()
                AppLogger.d(TAG, "Restored $restoredCount reminder(s) and scheduled Morning Wish after system event: $action")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to reschedule reminders after system event $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
