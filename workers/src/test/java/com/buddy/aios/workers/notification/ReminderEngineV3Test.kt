package com.buddy.aios.workers.notification

import com.buddy.aios.core.database.dao.TaskDao
import com.buddy.aios.core.database.entity.TaskEntity
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.ReminderDeliveryState
import com.buddy.aios.core.domain.entity.TaskStatus
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IReminderScheduler
import com.buddy.aios.core.domain.repository.ReminderEngineResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReminderEngineV3Test {

    private val taskDao: TaskDao = mockk(relaxed = true)
    private val scheduler: IReminderScheduler = mockk(relaxed = true)
    private val buddyModeRepository: IBuddyModeRepository = mockk(relaxed = true)

    private lateinit var reminderEngine: ReminderEngine
    private lateinit var timingMonitor: ReminderTimingMonitor
    private lateinit var nightlyAudit: NightlyReminderAudit

    @BeforeEach
    fun setUp() {
        coEvery { buddyModeRepository.getBuddyMode() } returns BuddyMode.ACTIVE
        coEvery { scheduler.canScheduleExactAlarms() } returns true
        coEvery { scheduler.schedule(any()) } returns true

        reminderEngine = ReminderEngine(
            context = mockk(relaxed = true),
            taskDao = taskDao,
            scheduler = scheduler,
            buddyModeRepository = buddyModeRepository,
        )

        timingMonitor = ReminderTimingMonitor()
        nightlyAudit = NightlyReminderAudit(taskDao, scheduler)
    }

    @Test
    fun `TEST 1 - ONE_TIME reminder completion archives it out of active reminders`() = runTest {
        val taskId = "task-one-time"
        val entity = TaskEntity(
            id = taskId,
            title = "Study Java",
            createdAt = 1000L,
            recurrenceRule = null, // ONE_TIME
            isCompleted = false,
            status = TaskStatus.PENDING.name,
        )

        coEvery { taskDao.getById(taskId) } returns entity

        val completed = reminderEngine.completeReminder(taskId)

        assertTrue(completed)
        coVerify(exactly = 1) { taskDao.markCompleted(taskId, true) }
        coVerify(exactly = 1) { taskDao.updateReminderSchedule(taskId, 0L, TaskStatus.COMPLETED.name, ReminderDeliveryState.DELIVERED.name) }
        coVerify(exactly = 1) { scheduler.cancel(taskId, any()) }
    }

    @Test
    fun `TEST 2 - RECURRING reminder completion calculates and schedules next occurrence while keeping logical reminder active`() = runTest {
        val taskId = "task-recurring"
        val entity = TaskEntity(
            id = taskId,
            title = "Push-ups",
            createdAt = 1000L,
            recurrenceRule = "DAILY", // RECURRING
            isCompleted = false,
            status = TaskStatus.PENDING.name,
        )

        coEvery { taskDao.getById(taskId) } returns entity

        val completed = reminderEngine.completeReminder(taskId)

        assertTrue(completed)
        // Must NOT call markCompleted(true) for recurring reminders!
        coVerify(exactly = 0) { taskDao.markCompleted(taskId, true) }
        // Must calculate and update next recurring time
        coVerify(exactly = 1) { taskDao.updateReminderSchedule(taskId, any(), TaskStatus.PENDING.name, ReminderDeliveryState.SCHEDULED.name) }
        coVerify(exactly = 1) { scheduler.schedule(any()) }
    }

    @Test
    fun `TEST 3 - ReminderTimingMonitor logs delay without corrupting requested target time`() {
        val requestedAt = 1000L
        val scheduledAt = 1000L
        val actualTriggeredAt = 1000L + (75 * 1000L) // 75 seconds delay (OS doze)

        val audit = timingMonitor.recordTrigger(
            reminderId = "r1",
            occurrenceId = 1,
            requestedAt = requestedAt,
            scheduledAt = scheduledAt,
            triggeredAt = actualTriggeredAt,
        )

        assertTrue(audit.isDelayed)
        assertEquals(75L, audit.differenceSeconds)
        assertEquals(scheduledAt, audit.scheduledAt, "Scheduled target time must remain unaltered")
    }

    @Test
    fun `TEST 4 - NightlyReminderAudit archives completed ONE_TIME reminders and preserves RECURRING reminders`() = runTest {
        val oneTimeCompleted = TaskEntity(id = "o1", title = "Call friend", createdAt = 1000L, isCompleted = true, recurrenceRule = null)
        val recurringActive = TaskEntity(id = "r1", title = "Push-ups", createdAt = 1000L, isCompleted = false, recurrenceRule = "DAILY")

        coEvery { taskDao.getPendingReminders() } returns listOf(oneTimeCompleted, recurringActive)

        val result = nightlyAudit.runAudit()

        assertEquals(1, result.completedOneTimeCleanedCount)
        assertEquals(1, result.activeRecurringPreservedCount)
        coVerify(exactly = 1) { taskDao.updateReminderSchedule("o1", 0L, TaskStatus.COMPLETED.name, "COMPLETED") }
    }
}
