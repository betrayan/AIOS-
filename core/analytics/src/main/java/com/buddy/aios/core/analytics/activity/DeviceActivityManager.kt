package com.buddy.aios.core.analytics.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.buddy.aios.core.common.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceActivitySnapshot(
    val lastScreenOffTime: Long = 0L,
    val lastScreenOnTime: Long = 0L,
    val isCharging: Boolean = false,
    val isInteractive: Boolean = true,
    val inactivityDurationMs: Long = 0L,
)

/**
 * Lightweight, low-power device activity monitor.
 *
 * Responsibilities:
 * - Listens for screen ON/OFF, device interactive, and charging state via [BroadcastReceiver].
 * - Measures overnight device inactivity duration without battery drain.
 * - Privacy-First & LOCAL ONLY: No raw sensor streams are saved, logged, or transmitted.
 */
@Singleton
class DeviceActivityManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "DeviceActivityManager"
    }

    private val _snapshot = MutableStateFlow(DeviceActivitySnapshot())
    val snapshot: StateFlow<DeviceActivitySnapshot> = _snapshot.asStateFlow()

    private val lastScreenOff = AtomicLong(System.currentTimeMillis() - (8 * 3600 * 1000L)) // Default 8h ago
    private val lastScreenOn = AtomicLong(System.currentTimeMillis())
    private var isReceiverRegistered = false

    private val activityReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    lastScreenOff.set(now)
                    AppLogger.d(TAG, "Screen OFF recorded at $now")
                    updateSnapshot(isInteractive = false)
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    val screenOff = lastScreenOff.get()
                    val inactivity = if (screenOff > 0) now - screenOff else 0L
                    lastScreenOn.set(now)
                    AppLogger.d(TAG, "Screen ON recorded at $now. Inactivity window: ${inactivity / 60000} mins")
                    updateSnapshot(isInteractive = true, inactivityMs = inactivity)
                }
                Intent.ACTION_POWER_CONNECTED -> updateSnapshot(isCharging = true)
                Intent.ACTION_POWER_DISCONNECTED -> updateSnapshot(isCharging = false)
            }
        }
    }

    fun startMonitoring() {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            context.registerReceiver(activityReceiver, filter)
            isReceiverRegistered = true

            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isInteractive = pm?.isInteractive ?: true
            updateSnapshot(isInteractive = isInteractive)
            AppLogger.d(TAG, "DeviceActivityManager monitoring started")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to register activity receiver", e)
        }
    }

    fun stopMonitoring() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(activityReceiver)
            isReceiverRegistered = false
            AppLogger.d(TAG, "DeviceActivityManager monitoring stopped")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to unregister activity receiver", e)
        }
    }

    private fun updateSnapshot(
        isInteractive: Boolean = _snapshot.value.isInteractive,
        isCharging: Boolean = _snapshot.value.isCharging,
        inactivityMs: Long = _snapshot.value.inactivityDurationMs,
    ) {
        _snapshot.value = DeviceActivitySnapshot(
            lastScreenOffTime = lastScreenOff.get(),
            lastScreenOnTime = lastScreenOn.get(),
            isCharging = isCharging,
            isInteractive = isInteractive,
            inactivityDurationMs = inactivityMs,
        )
    }
}
