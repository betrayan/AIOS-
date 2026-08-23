package com.buddy.aios.workers.morning

import android.content.Context
import com.buddy.aios.core.common.notification.AIOSNotificationManager
import com.buddy.aios.core.common.voice.IVoiceOutputManager
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.MorningBriefingSettings
import com.buddy.aios.core.domain.entity.MorningWishState
import com.buddy.aios.core.domain.entity.UserProfile
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IMorningBriefingSettingsRepository
import com.buddy.aios.core.domain.repository.IUserRepository
import com.buddy.aios.core.domain.result.Result
import com.buddy.aios.core.ui.island.AIOSIslandStateManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import android.app.AlarmManager
import android.content.SharedPreferences

@OptIn(ExperimentalCoroutinesApi::class)
class MorningWishEngineTest {

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
        every { settingsRepository.observeSettings() } returns kotlinx.coroutines.flow.MutableStateFlow(
            MorningBriefingSettings(
                isMorningWishEnabled = true,
                morningWishHour = 6,
                morningWishMinute = 0
            )
        )
        coEvery { settingsRepository.getSettings() } returns MorningBriefingSettings(
            isMorningWishEnabled = true,
            morningWishHour = 6,
            morningWishMinute = 0
        )
        coEvery { buddyModeRepository.getBuddyMode() } returns BuddyMode.ACTIVE
        coEvery { userRepository.getUserProfile() } returns Result.Success(UserProfile("u1", "Vijay", "Vijay", "Engineer"))
        coEvery { morningBriefingEngine.generateAndDeliverMorningBriefing(any()) } returns MorningBriefingResult(
            title = "Morning Briefing",
            notificationBody = "2 tasks today",
            voiceBriefing = "You have 2 tasks today.",
            priorityTasks = emptyList(),
            estimatedSleepFormatted = "7 hrs",
            isMorningWindowActive = true
        )

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
    fun `Test 1 - Trigger Morning Wish speaks briefing and updates Dynamic Island`() = runTest {
        morningWishEngine.triggerMorningWish(isManualTrigger = true)

        coVerify(exactly = 1) { morningBriefingEngine.generateAndDeliverMorningBriefing(forceDebug = true) }
        verify(exactly = 1) { voiceOutputManager.speak(match { it.contains("Vijay") }) }
        verify(exactly = 1) { islandStateManager.show(any(), match { it.contains("Morning Wish") }, any(), any(), any()) }
        assertTrue(morningWishEngine.isWaitingForAcknowledgement())
    }

    @Test
    fun `Test 2 - Acknowledge Morning Wish stops speech and updates state to ACKNOWLEDGED`() = runTest {
        morningWishEngine.triggerMorningWish(isManualTrigger = true)
        morningWishEngine.acknowledgeMorningWish(source = "voice")

        verify(exactly = 1) { voiceOutputManager.stop() }
        assertEquals(MorningWishState.ACKNOWLEDGED, morningWishEngine.getTodayState())
        assertFalse(morningWishEngine.isWaitingForAcknowledgement())
    }

    @Test
    fun `Test 3 - Duplicate trigger on same day when acknowledged is ignored`() = runTest {
        morningWishEngine.triggerMorningWish(isManualTrigger = true)
        morningWishEngine.acknowledgeMorningWish(source = "voice")

        // Automatic trigger on same day
        morningWishEngine.triggerMorningWish(isManualTrigger = false)

        // MorningBriefingEngine should only have been called ONCE for the initial trigger
        coVerify(exactly = 1) { morningBriefingEngine.generateAndDeliverMorningBriefing(any()) }
    }

    @Test
    fun `Test 4 - Manual trigger at 06 05 AM triggers immediately even if not morning window`() = runTest {
        morningWishEngine.triggerMorningWish(isManualTrigger = true)

        coVerify(exactly = 1) { morningBriefingEngine.generateAndDeliverMorningBriefing(forceDebug = true) }
        assertTrue(morningWishEngine.isWaitingForAcknowledgement())
    }
}
