package com.buddy.aios.workers.morning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.repository.IMorningWishEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver entry point for exact Morning Wish trigger alarms,
 * 10-minute repeat alarms, and notification interaction buttons.
 */
@AndroidEntryPoint
class MorningWishReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MorningWishReceiver"
    }

    @Inject
    lateinit var morningWishEngine: IMorningWishEngine

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        AppLogger.d(TAG, "Received broadcast intent with action: $action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    MorningWishEngine.ACTION_MORNING_WISH_TRIGGER -> {
                        morningWishEngine.triggerMorningWish(isManualTrigger = false, isRepeat = false)
                    }
                    MorningWishEngine.ACTION_MORNING_WISH_REPEAT -> {
                        val count = intent.getIntExtra(MorningWishEngine.EXTRA_REPEAT_COUNT, 1)
                        morningWishEngine.triggerMorningWish(isManualTrigger = false, isRepeat = true, repeatCount = count)
                    }
                    MorningWishEngine.ACTION_MORNING_WISH_ACKNOWLEDGE -> {
                        morningWishEngine.acknowledgeMorningWish(source = "notification_talk")
                    }
                    MorningWishEngine.ACTION_MORNING_WISH_SILENCE -> {
                        morningWishEngine.acknowledgeMorningWish(source = "notification_silence")
                    }
                    Intent.ACTION_BOOT_COMPLETED -> {
                        morningWishEngine.scheduleMorningWish()
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error processing Morning Wish broadcast", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
