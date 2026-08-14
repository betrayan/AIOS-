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
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        AppLogger.d(TAG, "Device boot / package update completed — restoring pending reminder & morning wish alarms")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val restoredCount = reminderEngine.restoreReminders()
                morningWishEngine.scheduleMorningWish()
                AppLogger.d(TAG, "Restored $restoredCount reminder(s) and scheduled Morning Wish after reboot/update")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to reschedule reminders after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
