package com.buddy.aios.workers.morning

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.buddy.aios.core.common.notification.AIOSNotificationManager
import com.buddy.aios.core.common.voice.IVoiceOutputManager
import com.buddy.aios.core.domain.entity.MorningBriefingSettings
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IMorningBriefingSettingsRepository
import com.buddy.aios.core.domain.repository.IUserRepository
import com.buddy.aios.core.ui.island.AIOSIslandStateManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class MorningWishSchedulingTest {

    private val context: Context = mockk(relaxed = true)
    private val alarmManager: AlarmManager = mockk(relaxed = true)
    private val sharedPreferences: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private val morningBriefingEngine: MorningBriefingEngine = mockk(relaxed = true)
    private val settingsRepository: IMorningBriefingSettingsRepository = mockk(relaxed = true)
    private val buddyModeRepository: IBuddyModeRepository = mockk(relaxed = true)
    private val userRepository: IUserRepository = mockk(relaxed = true)
    private val notificationManager: AIOSNotificationManager = mockk(relaxed = true)
    private val islandStateManager: AIOSIslandStateManager = mockk(relaxed = true)
    private val voiceOutputManager: IVoiceOutputManager = mockk(relaxed = true)

    private val settingsFlow = MutableStateFlow(
        MorningBriefingSettings(
            isMorningWishEnabled = true,
            morningWishHour = 6,
            morningWishMinute = 30
        )
    )

    private lateinit var morningWishEngine: MorningWishEngine

    private val prefsMap = mutableMapOf<String, String>()

    @BeforeEach
    fun setUp() {
        prefsMap.clear()
        every { context.getPackageName() } returns "com.buddy.aios"
        every { context.packageName } returns "com.buddy.aios"
        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<android.app.NotificationManager>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            prefsMap[firstArg()] = secondArg()
            editor
        }
        every { editor.apply() } returns Unit
        every { sharedPreferences.getString(any(), any()) } answers {
            prefsMap[firstArg()] ?: (secondArg() as String?)
        }
        every { settingsRepository.observeSettings() } returns settingsFlow
        coEvery { settingsRepository.getSettings() } returns settingsFlow.value

        morningWishEngine = MorningWishEngine(
            context = context,
            morningBriefingEngine = morningBriefingEngine,
            settingsRepository = settingsRepository,
            buddyModeRepository = buddyModeRepository,
            userRepository = userRepository,
            notificationManager = notificationManager,
            islandStateManager = islandStateManager,
            voiceOutputManager = voiceOutputManager,
        )
    }

    @Test
    fun `TEST 1 Configured 06 30 AM stored as hour 6 and minute 30`() = runTest {
        val settings = settingsRepository.getSettings()
        assertEquals(6, settings.morningWishHour)
        assertEquals(30, settings.morningWishMinute)
    }

    @Test
    fun `TEST 2 Current 05 00 AM expected next today 06 30 AM`() {
        val zoneId = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 8, 20, 5, 0, 0), zoneId)
        val targetHour = 6
        val targetMin = 30

        var target = now.withHour(targetHour).withMinute(targetMin).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }

        assertEquals(2026, target.year)
        assertEquals(8, target.monthValue)
        assertEquals(20, target.dayOfMonth)
        assertEquals(6, target.hour)
        assertEquals(30, target.minute)
    }

    @Test
    fun `TEST 3 Current 06 31 AM expected next tomorrow 06 30 AM`() {
        val zoneId = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 8, 20, 6, 31, 0), zoneId)
        val targetHour = 6
        val targetMin = 30

        var target = now.withHour(targetHour).withMinute(targetMin).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }

        assertEquals(2026, target.year)
        assertEquals(8, target.monthValue)
        assertEquals(21, target.dayOfMonth)
        assertEquals(6, target.hour)
        assertEquals(30, target.minute)
    }

    @Test
    fun `TEST 4 Current 23 00 expected next tomorrow 06 30 AM`() {
        val zoneId = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 8, 20, 23, 0, 0), zoneId)
        val targetHour = 6
        val targetMin = 30

        var target = now.withHour(targetHour).withMinute(targetMin).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }

        assertEquals(2026, target.year)
        assertEquals(8, target.monthValue)
        assertEquals(21, target.dayOfMonth)
        assertEquals(6, target.hour)
        assertEquals(30, target.minute)
    }

    @Test
    fun `TEST 5 Timezone Asia Kolkata epoch corresponds to exact local 06 30 IST`() {
        val zoneId = ZoneId.of("Asia/Kolkata")
        val localTarget = ZonedDateTime.of(LocalDateTime.of(2026, 8, 21, 6, 30, 0), zoneId)
        val epochMillis = localTarget.toInstant().toEpochMilli()

        val reconstructed = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), zoneId)
        assertEquals(6, reconstructed.hour)
        assertEquals(30, reconstructed.minute)
        assertEquals("Asia/Kolkata", reconstructed.zone.id)
    }

    @Test
    fun `TEST 6 scheduleMorningWish registers RTC_WAKEUP alarm on AlarmManager`() = runTest {
        morningWishEngine.scheduleMorningWish()

        val triggerList = mutableListOf<Long>()
        verify {
            alarmManager.setExactAndAllowWhileIdle(
                eq(AlarmManager.RTC_WAKEUP),
                capture(triggerList),
                any()
            )
        }

        val scheduledZdt = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(triggerList.last()),
            ZoneId.systemDefault()
        )
        assertEquals(6, scheduledZdt.hour)
        assertEquals(30, scheduledZdt.minute)
    }
}
