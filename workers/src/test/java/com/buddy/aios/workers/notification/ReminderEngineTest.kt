package com.buddy.aios.workers.notification

import android.content.Context
import com.buddy.aios.core.database.dao.TaskDao
import com.buddy.aios.core.database.entity.TaskEntity
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IReminderScheduler
import com.buddy.aios.core.domain.repository.ReminderEngineResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderEngineTest {

    private val context: Context = mockk(relaxed = true)
    private val taskDao: TaskDao = mockk(relaxed = true)
    private val scheduler: IReminderScheduler = mockk(relaxed = true)
    private val buddyModeRepository: IBuddyModeRepository = mockk(relaxed = true)

    private lateinit var reminderEngine: ReminderEngine

    @BeforeEach
    fun setUp() {
        coEvery { buddyModeRepository.getBuddyMode() } returns BuddyMode.ACTIVE
        every { scheduler.canScheduleExactAlarms() } returns true
        coEvery { scheduler.schedule(any()) } returns true

        reminderEngine = ReminderEngine(
            context = context,
            taskDao = taskDao,
            scheduler = scheduler,
            buddyModeRepository = buddyModeRepository,
        )
    }

    @Test
    fun `Test 1 - Create valid reminder persists in DB and schedules alarm`() = runTest {
        val title = "Study Java"
        val triggerTime = System.currentTimeMillis() + 3600_000L

        coEvery { taskDao.getById(any()) } returns TaskEntity(
            id = "t1",
            title = title,
            createdAt = System.currentTimeMillis(),
            dueDate = triggerTime,
            reminderTime = triggerTime,
        )

        val result = reminderEngine.createReminder(
            title = title,
            description = "Practice concurrency",
            triggerTimestamp = triggerTime,
            recurrenceRule = null,
            voiceEnabled = true,
        )

        assertTrue(result is ReminderEngineResult.Success)
        val success = result as ReminderEngineResult.Success
        assertTrue(success.confirmationMessage.contains("Study Java"))
        coVerify(exactly = 1) { taskDao.insert(any()) }
        coVerify(exactly = 1) { scheduler.schedule(any()) }
    }

    @Test
    fun `Test 2 - Past time protection moves past trigger time to next valid occurrence`() = runTest {
        val title = "Daily Standup"
        val pastTime = System.currentTimeMillis() - 7200_000L // 2 hours ago

        coEvery { taskDao.getById(any()) } returns TaskEntity(
            id = "t1",
            title = title,
            createdAt = System.currentTimeMillis(),
        )

        val result = reminderEngine.createReminder(
            title = title,
            description = "",
            triggerTimestamp = pastTime,
            recurrenceRule = "DAILY",
            voiceEnabled = true,
        )

        assertTrue(result is ReminderEngineResult.Success)
        val success = result as ReminderEngineResult.Success
        assertTrue(success.task.reminderTime!! > System.currentTimeMillis())
    }

    @Test
    fun `Test 3 - Exact alarm permission missing returns PermissionRequired status`() = runTest {
        every { scheduler.canScheduleExactAlarms() } returns false

        val result = reminderEngine.createReminder(
            title = "Study Java",
            description = "",
            triggerTimestamp = System.currentTimeMillis() + 3600_000L,
            recurrenceRule = null,
            voiceEnabled = true,
        )

        assertTrue(result is ReminderEngineResult.PermissionRequired)
        val perm = result as ReminderEngineResult.PermissionRequired
        assertEquals("SCHEDULE_EXACT_ALARM", perm.permissionName)
    }

    @Test
    fun `Test 4 - Cancel reminder stops scheduled alarm and updates state to CANCELLED`() = runTest {
        val taskId = "task_123"
        coEvery { taskDao.getById(taskId) } returns TaskEntity(
            id = taskId,
            title = "Call Mom",
            createdAt = System.currentTimeMillis(),
            notificationId = 1001,
        )

        val cancelled = reminderEngine.cancelReminder(taskId)

        coVerify(exactly = 1) { scheduler.cancel(match<Task> { it.id == taskId }) }
        coVerify(exactly = 1) { taskDao.updateReminderSchedule(taskId, 0L, "CANCELLED", "CANCELLED") }
    }

    @Test
    fun `Test 5 - Snooze reminder reschedules alarm for snooze minutes`() = runTest {
        val taskId = "task_456"
        val entity = TaskEntity(
            id = taskId,
            title = "Workout",
            createdAt = System.currentTimeMillis(),
            notificationId = 2002,
        )
        coEvery { taskDao.getById(taskId) } returns entity

        val result = reminderEngine.snoozeReminder(taskId, snoozeMinutes = 10)

        assertTrue(result is ReminderEngineResult.Success)
        val success = result as ReminderEngineResult.Success
        assertTrue(success.confirmationMessage.contains("10 minutes"))
        coVerify(exactly = 1) { scheduler.cancel(taskId, 2002) }
        coVerify(exactly = 1) { scheduler.schedule(any()) }
    }

    @Test
    fun `Test 6 - Restore reminders after boot reschedules pending future alarms`() = runTest {
        val futureTime = System.currentTimeMillis() + 7200_000L
        val pendingList = listOf(
            TaskEntity(id = "t1", title = "Task 1", createdAt = 100L, reminderTime = futureTime, isCompleted = false),
            TaskEntity(id = "t2", title = "Task 2", createdAt = 100L, reminderTime = futureTime, isCompleted = false),
        )
        coEvery { taskDao.getPendingReminders() } returns pendingList

        val restored = reminderEngine.restoreReminders()

        assertEquals(2, restored)
        coVerify(exactly = 2) { scheduler.schedule(any()) }
    }

    @Test
    fun `Test 7 - BuddyMode OFF suppresses reminder handling`() = runTest {
        coEvery { buddyModeRepository.getBuddyMode() } returns BuddyMode.OFF

        val handled = reminderEngine.handleReminderTriggered("t1", 1001)

        assertFalse(handled)
    }

    @Test
    fun `Test 8 - Complete reminder marks completed in DB and cancels alarm`() = runTest {
        val taskId = "t1"
        coEvery { taskDao.getById(taskId) } returns TaskEntity(
            id = taskId,
            title = "Task 1",
            createdAt = 100L,
            notificationId = 555,
        )

        val completed = reminderEngine.completeReminder(taskId)

        assertTrue(completed)
        coVerify(exactly = 1) { taskDao.markCompleted(taskId, true) }
        coVerify(exactly = 1) { scheduler.cancel(taskId, 555) }
    }

    @Test
    fun `Test 9 - One-time reminder trigger marks task completed and archives from active list`() = runTest {
        val taskId = "t_onetime"
        coEvery { taskDao.getById(taskId) } returns TaskEntity(
            id = taskId,
            title = "One-time test",
            createdAt = System.currentTimeMillis(),
            recurrenceRule = null,
            isCompleted = false,
        )

        val handled = reminderEngine.handleReminderTriggered(taskId, 1001)

        assertTrue(handled)
        coVerify(exactly = 1) { taskDao.markCompleted(taskId, true) }
        coVerify(exactly = 1) { taskDao.updateReminderSchedule(taskId, 0L, "COMPLETED", "DELIVERED") }
    }

    @Test
    fun `Test 10 - Recurring reminder trigger reschedules next occurrence without completing task`() = runTest {
        val taskId = "t_recurring"
        coEvery { taskDao.getById(taskId) } returns TaskEntity(
            id = taskId,
            title = "Daily standup",
            createdAt = System.currentTimeMillis(),
            recurrenceRule = "DAILY",
            isCompleted = false,
        )

        val handled = reminderEngine.handleReminderTriggered(taskId, 1002)

        assertTrue(handled)
        coVerify(exactly = 0) { taskDao.markCompleted(taskId, true) }
        coVerify(exactly = 1) { taskDao.updateReminderSchedule(taskId, match { it > System.currentTimeMillis() }, "PENDING", "SCHEDULED") }
    }

    @Test
    fun `Test 11 - Reminder in 2 minutes sets trigger approximately 120 seconds in the future`() = runTest {
        val now = System.currentTimeMillis()
        val twoMinutesMs = 2 * 60 * 1000L
        val requestedTrigger = now + twoMinutesMs

        coEvery { taskDao.getById(any()) } returns TaskEntity(
            id = "t_2min",
            title = "test reminder A",
            createdAt = now,
            reminderTime = requestedTrigger,
        )

        val result = reminderEngine.createReminder(
            title = "test reminder A",
            description = "",
            triggerTimestamp = requestedTrigger,
            recurrenceRule = null,
            voiceEnabled = false,
        )

        assertTrue(result is ReminderEngineResult.Success)
        val success = result as ReminderEngineResult.Success
        // Must be strictly in the future (not rounded to the minute boundary)
        assertTrue(success.task.reminderTime!! > now, "reminderTime must be in the future")
        // Must be within ±5 seconds of the expected 2-minute offset
        val diff = success.task.reminderTime!! - requestedTrigger
        assertTrue(kotlin.math.abs(diff) < 5000L, "reminder should be ~2 minutes from now, diff=$diff ms")
    }

    @Test
    fun `Test 12 - Reminder in 10 minutes sets trigger approximately 600 seconds in the future`() = runTest {
        val now = System.currentTimeMillis()
        val tenMinutesMs = 10 * 60 * 1000L
        val requestedTrigger = now + tenMinutesMs

        coEvery { taskDao.getById(any()) } returns TaskEntity(
            id = "t_10min",
            title = "test reminder B",
            createdAt = now,
            reminderTime = requestedTrigger,
        )

        val result = reminderEngine.createReminder(
            title = "test reminder B",
            description = "",
            triggerTimestamp = requestedTrigger,
            recurrenceRule = null,
            voiceEnabled = false,
        )

        assertTrue(result is ReminderEngineResult.Success)
        val success = result as ReminderEngineResult.Success
        assertTrue(success.task.reminderTime!! > now, "reminderTime must be in the future")
        val diff = success.task.reminderTime!! - requestedTrigger
        assertTrue(kotlin.math.abs(diff) < 5000L, "reminder should be ~10 minutes from now, diff=$diff ms")
    }

    @Test
    fun `Test 13 - Two separate reminders have distinct taskIds and distinct notificationIds`() = runTest {
        val now = System.currentTimeMillis()
        val triggerA = now + 2 * 60 * 1000L
        val triggerB = now + 10 * 60 * 1000L

        // Each call to createReminder generates a new UUID → different taskId → different notificationId (hashCode)
        coEvery { taskDao.getById(any()) } answers {
            val id = firstArg<String>()
            TaskEntity(id = id, title = "reminder", createdAt = now, reminderTime = triggerA)
        }

        val resultA = reminderEngine.createReminder(
            title = "test reminder A",
            description = "",
            triggerTimestamp = triggerA,
            recurrenceRule = null,
            voiceEnabled = false,
        )
        val resultB = reminderEngine.createReminder(
            title = "test reminder B",
            description = "",
            triggerTimestamp = triggerB,
            recurrenceRule = null,
            voiceEnabled = false,
        )

        assertTrue(resultA is ReminderEngineResult.Success)
        assertTrue(resultB is ReminderEngineResult.Success)

        val taskA = (resultA as ReminderEngineResult.Success).task
        val taskB = (resultB as ReminderEngineResult.Success).task

        // Task IDs must be unique (UUID-based)
        assertTrue(taskA.id != taskB.id, "Task IDs must differ: ${taskA.id} vs ${taskB.id}")

        // Notification IDs must differ (so PendingIntent requestCodes differ → alarms are independent)
        assertTrue(taskA.notificationId != taskB.notificationId,
            "NotificationIds must differ: ${taskA.notificationId} vs ${taskB.notificationId}")

        // Trigger times must be preserved separately
        assertEquals(triggerA, taskA.reminderTime, "Reminder A trigger time must match")
        assertEquals(triggerB, taskB.reminderTime, "Reminder B trigger time must match")
    }
}
