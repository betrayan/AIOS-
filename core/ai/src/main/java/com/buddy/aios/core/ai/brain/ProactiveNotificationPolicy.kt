package com.buddy.aios.core.ai.brain

import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.BuddyMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anti-nagging policy governor for proactive notifications.
 * Prevents notification spam, repeated alerts, and respects ProactiveMode & BuddyMode.
 */
@Singleton
class ProactiveNotificationPolicy @Inject constructor() {

    companion object {
        private const val TAG = "ProactiveNotificationPolicy"
        private const val MIN_NOTIFICATION_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    }

    private var activeMode: ProactiveMode = ProactiveMode.BALANCED
    private val deliveredHashes = mutableMapOf<String, Long>()
    private val dismissedHashes = mutableSetOf<String>()

    fun setProactiveMode(mode: ProactiveMode) {
        AppLogger.d(TAG, "ProactiveMode updated to: $mode")
        this.activeMode = mode
    }

    fun getProactiveMode(): ProactiveMode = activeMode

    fun shouldDeliverProactiveAlert(
        alertHash: String,
        priority: PriorityLevel,
        buddyMode: BuddyMode,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        // 1. BuddyMode OFF or ProactiveMode OFF -> Suppress all proactive alerts!
        if (buddyMode == BuddyMode.OFF || activeMode == ProactiveMode.OFF) {
            AppLogger.d(TAG, "Alert suppressed: BuddyMode=$buddyMode, ProactiveMode=$activeMode")
            return false
        }

        // 2. User dismissed this specific alert hash -> Suppress!
        if (dismissedHashes.contains(alertHash)) {
            AppLogger.d(TAG, "Alert suppressed: Hash $alertHash was explicitly dismissed by user")
            return false
        }

        // 3. Mode filtering
        when (activeMode) {
            ProactiveMode.QUIET -> if (priority != PriorityLevel.CRITICAL) return false
            ProactiveMode.BALANCED -> if (priority != PriorityLevel.CRITICAL && priority != PriorityLevel.HIGH) return false
            else -> {}
        }

        // 4. Rate limiting & Deduplication
        if (deliveredHashes.containsKey(alertHash)) {
            val lastDelivered = deliveredHashes[alertHash] ?: 0L
            if (now - lastDelivered < MIN_NOTIFICATION_INTERVAL_MS) {
                AppLogger.d(TAG, "Alert suppressed: Hash $alertHash delivered recently (${(now - lastDelivered)/1000}s ago)")
                return false
            }
        }

        deliveredHashes[alertHash] = now
        return true
    }

    fun dismissAlert(alertHash: String) {
        dismissedHashes.add(alertHash)
        AppLogger.d(TAG, "Alert hash $alertHash added to dismissed list")
    }

    fun generateConsolidatedProactiveSuggestion(snapshot: AIOSContextSnapshot): String? {
        val hasTravel = snapshot.situations.contains(SituationFlag.TRAVEL_DAY)
        val hasLowBattery = snapshot.situations.contains(SituationFlag.LOW_BATTERY)
        val hasRain = snapshot.situations.contains(SituationFlag.RAIN_RISK)

        if (!hasTravel && !hasLowBattery && !hasRain) return null

        return when {
            hasTravel && hasLowBattery && hasRain ->
                "You're traveling early. Your battery is ${snapshot.batteryLevel}% and rain is forecasted, so consider charging your phone tonight and packing an umbrella."
            hasTravel && hasLowBattery ->
                "You have upcoming travel and your battery is at ${snapshot.batteryLevel}%. Consider charging your phone before leaving."
            hasTravel && hasRain ->
                "Rain is expected during your upcoming trip. Don't forget an umbrella."
            hasLowBattery && (snapshot.batteryLevel ?: 100) <= 15 ->
                "Your device battery is at ${snapshot.batteryLevel}%. Plug in your charger to avoid missing reminders."
            else -> null
        }
    }
}
