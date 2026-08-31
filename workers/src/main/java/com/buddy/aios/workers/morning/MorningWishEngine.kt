
package com.buddy.aios.workers.morning

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.common.notification.AIOSNotificationManager
import com.buddy.aios.core.common.voice.IVoiceOutputManager
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.MorningWishState
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IMorningBriefingSettingsRepository
import com.buddy.aios.core.domain.repository.IMorningWishEngine
import com.buddy.aios.core.domain.repository.IUserRepository
import com.buddy.aios.core.domain.result.Result
import com.buddy.aios.core.ui.island.AIOSIslandState
import com.buddy.aios.core.ui.island.AIOSIslandStateManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Master engine for AIOS Morning Wish interactive voice alarm.
 *
 * Responsibilities:
 * - Exact RTC_WAKEUP alarm scheduling in local timezone using ZonedDateTime
 * - Daily identity state machine (morning_wish_YYYY_MM_DD)
 * - Time-aware natural voice greetings & briefing delivery
 * - 10-minute repeat cycle until acknowledged
 * - Intercepting voice & volume button acknowledgements
 * - Updating Dynamic Island pill state
 */
@Singleton
class MorningWishEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val morningBriefingEngine: MorningBriefingEngine,
    private val settingsRepository: IMorningBriefingSettingsRepository,
    private val buddyModeRepository: IBuddyModeRepository,
    private val userRepository: IUserRepository,
    private val notificationManager: AIOSNotificationManager,
    private val islandStateManager: AIOSIslandStateManager,
    private val voiceOutputManager: IVoiceOutputManager,
) : IMorningWishEngine {

    companion object {
        private const val TAG = "MorningWishEngine"
        private const val PREFS_NAME = "aios_morning_wish_prefs"
        private const val NOTIFICATION_ID = 88001

        const val ACTION_MORNING_WISH_TRIGGER = "com.buddy.aios.ACTION_MORNING_WISH_TRIGGER"
        const val ACTION_MORNING_WISH_REPEAT = "com.buddy.aios.ACTION_MORNING_WISH_REPEAT"
        const val ACTION_MORNING_WISH_ACKNOWLEDGE = "com.buddy.aios.ACTION_MORNING_WISH_ACKNOWLEDGE"
        const val ACTION_MORNING_WISH_SILENCE = "com.buddy.aios.ACTION_MORNING_WISH_SILENCE"
        const val EXTRA_REPEAT_COUNT = "extra_repeat_count"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                settingsRepository.observeSettings()
                    .distinctUntilChangedBy { Triple(it.isMorningWishEnabled, it.morningWishHour, it.morningWishMinute) }
                    .collect {
                        scheduleMorningWish()
                    }
            } catch (e: Throwable) {
                AppLogger.w(TAG, "Skipped observing settings flow: ${e.message}")
            }
        }
    }

    override suspend fun scheduleMorningWish() {
        val settings = settingsRepository.getSettings()
        if (!settings.isMorningWishEnabled) {
            AppLogger.d(TAG, "Morning Wish is disabled in settings — cancelling pending alarm")
            cancelPendingMorningWishAlarm()
            return
        }

        // ── Step 1: Log stored values exactly as read from SharedPreferences ──
        AppLogger.d(TAG, "scheduleMorningWish: stored hour=${settings.morningWishHour} minute=${settings.morningWishMinute}")

        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zoneId)

        // ── Step 2: Construct target in device local timezone (no UTC conversion) ──
        var target = now.withHour(settings.morningWishHour)
            .withMinute(settings.morningWishMinute)
            .withSecond(0)
            .withNano(0)

        AppLogger.d(TAG, "scheduleMorningWish: now=$now  raw-target=$target")

        // ── Step 3: Roll to next day if target has already passed today ──
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
            AppLogger.d(TAG, "scheduleMorningWish: target passed — rolled to next day: $target")
        }

        val triggerAtMillis = target.toInstant().toEpochMilli()

        // ── Step 4: Verify epoch corresponds to intended local time ──
        val verifiedLocal = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(triggerAtMillis), zoneId)
        AppLogger.d(TAG, "scheduleMorningWish: EPOCH=$triggerAtMillis  verified-local-hour=${verifiedLocal.hour}  verified-local-minute=${verifiedLocal.minute}  zone=${zoneId.id}")

        val intent = Intent(context, MorningWishReceiver::class.java).apply {
            action = ACTION_MORNING_WISH_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    val formatStr = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.ENGLISH).format(target)
                    AppLogger.d(TAG, "scheduleMorningWish: SUCCESS — Exact AlarmManager registered for $formatStr (millis=$triggerAtMillis)")
                } else {
                    AppLogger.w(TAG, "canScheduleExactAlarms is false — falling back to setAndAllowWhileIdle")
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                val formatStr = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.ENGLISH).format(target)
                AppLogger.d(TAG, "scheduleMorningWish: SUCCESS — AlarmManager registered for $formatStr (millis=$triggerAtMillis)")
            }
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "Failed to schedule exact Morning Wish alarm due to permission — falling back to setAndAllowWhileIdle", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (fallbackEx: Exception) {
                AppLogger.e(TAG, "Fallback setAndAllowWhileIdle also failed", fallbackEx)
            }
        }
    }

    private fun cancelPendingMorningWishAlarm() {
        val intent = Intent(context, MorningWishReceiver::class.java).apply {
            action = ACTION_MORNING_WISH_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    override suspend fun triggerMorningWish(
        isManualTrigger: Boolean,
        isRepeat: Boolean,
        repeatCount: Int,
    ) {
        val todayStr = getTodayDateString()
        val currentState = getTodayState()

        if (currentState == MorningWishState.ACKNOWLEDGED && !isManualTrigger) {
            AppLogger.d(TAG, "Morning Wish already acknowledged today ($todayStr) — ignoring automatic trigger")
            return
        }

        val settings = settingsRepository.getSettings()
        if (!settings.isMorningWishEnabled && !isManualTrigger) {
            AppLogger.d(TAG, "Morning Wish is disabled")
            return
        }

        saveStateForToday(MorningWishState.WAITING_FOR_ACK)

        val userName = (userRepository.getUserProfile() as? Result.Success)?.value?.name ?: "Vijay"
        val nowLocal = ZonedDateTime.now()
        val greeting = getTimeAwareGreeting(userName, nowLocal)

        val briefingResult = morningBriefingEngine.generateAndDeliverMorningBriefing(
            forceDebug = isManualTrigger,
            suppressNotification = true, // MorningWishEngine delivers its own richer notification below
        )

        val hour = nowLocal.hour
        val minute = nowLocal.minute
        val displayHour = if (hour % 12 == 0) 12 else hour % 12
        val amPm = if (hour < 12) "AM" else "PM"
        val spokenTime = if (minute == 0) String.format(Locale.ENGLISH, "%d %s", displayHour, amPm) else String.format(Locale.ENGLISH, "%d:%02d", displayHour, minute)

        val speechText = when {
            !isRepeat -> {
                briefingResult.voiceBriefing
            }
            repeatCount == 1 -> {
                "Buddy, good morning. It's already $spokenTime. Your morning briefing is still waiting."
            }
            repeatCount == 2 -> {
                "Buddy, it's $spokenTime already. Shall we start the day?"
            }
            else -> {
                "Buddy, it's $spokenTime. Whenever you're ready, your morning briefing is here."
            }
        }

        // 1. Instantly schedule tomorrow's Morning Wish alarm (locks in next day even if user ignores today's alarm)
        if (!isRepeat && !isManualTrigger) {
            scheduleMorningWish()
        }

        // 2. Post Morning Wish Notification FIRST (ensures notification appears regardless of TTS state)
        // Use the rich computed body from MorningBriefingEngine (task count, times, weather)
        val notifBody = briefingResult.notificationBody.ifBlank { briefingResult.voiceBriefing.take(120) }
        deliverNotification(greeting, notifBody)

        // 3. Speak summary via TTS with Audio Focus
        val buddyMode = buddyModeRepository.getBuddyMode()
        AppLogger.d(TAG, "triggerMorningWish: buddyMode=$buddyMode, willSpeak=${buddyMode != BuddyMode.OFF && buddyMode != BuddyMode.SILENT}, speechLength=${speechText.length}")
        if (buddyMode != BuddyMode.OFF && buddyMode != BuddyMode.SILENT) {
            requestAudioFocus()
            voiceOutputManager.speak(speechText)
        }

        // 4. Update Dynamic Island Pill with interactive navigation callback
        val mainAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "chat")
        }

        islandStateManager.show(
            state = AIOSIslandState.REMINDER,
            message = "🌅 Morning Wish • Waiting for response",
            autoDismissMs = 0L,
            actionLabel = "Open Chat",
            onAction = {
                mainAppIntent?.let { intent ->
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to start activity from Dynamic Island onAction", e)
                    }
                }
            }
        )

        // 5. Schedule 10-minute repeat (up to 3 times / 30 mins) if not acknowledged
        if (repeatCount <= 3) {
            scheduleRepeatAlarm(repeatCount + 1)
        } else {
            AppLogger.d(TAG, "Max Morning Wish repeats reached ($repeatCount) — stopping repeat alarms for today")
        }
    }

    override suspend fun acknowledgeMorningWish(source: String) {
        AppLogger.d(TAG, "Acknowledging Morning Wish from source: $source")

        val targetState = if (source == "notification_silence" || source == "silence") {
            MorningWishState.DISMISSED
        } else {
            MorningWishState.ACKNOWLEDGED
        }

        saveStateForToday(targetState)

        // 1. Stop 10-minute repeat alarm
        cancelRepeatAlarm()

        // 2. Stop active speech immediately
        voiceOutputManager.stop()

        // 3. Dismiss notification
        try {
            val notificationManagerCompat = NotificationManagerCompat.from(context)
            notificationManagerCompat.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Notification cancel skipped: ${e.message}")
        }

        // 4. Update Dynamic Island
        val islandMessage = if (targetState == MorningWishState.DISMISSED) "🌅 Morning Wish Silenced" else "🌅 Morning Wish Acknowledged"
        islandStateManager.show(
            state = AIOSIslandState.TASK_CREATED,
            message = islandMessage,
            autoDismissMs = 2500L
        )

        // 5. Schedule next day's Morning Wish
        scheduleMorningWish()
    }

    override suspend fun getTodayState(): MorningWishState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayKey = "morning_wish_${getTodayDateString()}"
        val stateName = prefs.getString(todayKey, MorningWishState.NOT_TRIGGERED.name)
        return try {
            MorningWishState.valueOf(stateName ?: MorningWishState.NOT_TRIGGERED.name)
        } catch (e: Exception) {
            MorningWishState.NOT_TRIGGERED
        }
    }

    override fun isWaitingForAcknowledgement(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayKey = "morning_wish_${getTodayDateString()}"
        val stateName = prefs.getString(todayKey, MorningWishState.NOT_TRIGGERED.name)
        return stateName == MorningWishState.WAITING_FOR_ACK.name
    }

    private fun saveStateForToday(state: MorningWishState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayKey = "morning_wish_${getTodayDateString()}"
        prefs.edit().putString(todayKey, state.name).apply()
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy_MM_dd", Locale.ENGLISH).format(Date())
    }

    private fun getTimeAwareGreeting(userName: String, dateTime: ZonedDateTime = ZonedDateTime.now()): String {
        val hour = dateTime.hour
        val minute = dateTime.minute
        val displayHour = if (hour % 12 == 0) 12 else hour % 12
        val amPm = if (hour < 12) "AM" else "PM"
        val timeFormatted = String.format(Locale.ENGLISH, "%d:%02d %s", displayHour, minute, amPm)

        val greetingPrefix = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
        return "$greetingPrefix, $userName. It is $timeFormatted."
    }

    private fun requestAudioFocus() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager?.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(null, android.media.AudioManager.STREAM_ALARM, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to request audio focus for Morning Wish speech: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun deliverNotification(greeting: String, body: String) {
        try {
            val userName = (userRepository.getUserProfile() as? Result.Success)?.value?.name ?: "Vijay"

            val ackIntent = Intent(context, MorningWishReceiver::class.java).apply {
                action = ACTION_MORNING_WISH_ACKNOWLEDGE
            }
            val ackPendingIntent = PendingIntent.getBroadcast(
                context,
                2001,
                ackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val silenceIntent = Intent(context, MorningWishReceiver::class.java).apply {
                action = ACTION_MORNING_WISH_SILENCE
            }
            val silencePendingIntent = PendingIntent.getBroadcast(
                context,
                2002,
                silenceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val publicNotification = NotificationCompat.Builder(context, AIOSNotificationManager.CHANNEL_MORNING)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🌅 AIOS Morning Wish")
                .setContentText("Good morning $userName. Your AIOS morning briefing is ready.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            val builder = NotificationCompat.Builder(context, AIOSNotificationManager.CHANNEL_MORNING)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🌅 AIOS Morning Wish")
                .setContentText("Good morning $userName. Your AIOS morning briefing is ready.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Good morning $userName. Your AIOS morning briefing is ready."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicNotification)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_call, "Acknowledge", ackPendingIntent)
                .addAction(android.R.drawable.ic_media_pause, "Silence", silencePendingIntent)

            if (notificationManager.hasNotificationPermission()) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Notification post skipped or exception in current environment", e)
        }
    }

    private fun scheduleRepeatAlarm(nextRepeatCount: Int) {
        val triggerTime = System.currentTimeMillis() + 10 * 60 * 1000L // 10 minutes
        val intent = Intent(context, MorningWishReceiver::class.java).apply {
            action = ACTION_MORNING_WISH_REPEAT
            putExtra(EXTRA_REPEAT_COUNT, nextRepeatCount)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            AppLogger.d(TAG, "Scheduled 10-minute Morning Wish repeat alarm #$nextRepeatCount")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to schedule repeat alarm", e)
        }
    }

    private fun cancelRepeatAlarm() {
        val intent = Intent(context, MorningWishReceiver::class.java).apply {
            action = ACTION_MORNING_WISH_REPEAT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1002,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
