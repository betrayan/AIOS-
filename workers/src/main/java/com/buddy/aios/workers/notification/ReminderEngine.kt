package com.buddy.aios.workers.notification

import android.content.Context
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.data.mapper.toDomain
import com.buddy.aios.core.data.mapper.toEntity
import com.buddy.aios.core.database.dao.TaskDao
import com.buddy.aios.core.database.entity.TaskEntity
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.ReminderDeliveryState
import com.buddy.aios.core.domain.entity.RepeatRule
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority
import com.buddy.aios.core.domain.entity.TaskStatus
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IReminderEngine
import com.buddy.aios.core.domain.repository.IReminderScheduler
import com.buddy.aios.core.domain.repository.ReminderEngineResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single master orchestration engine for AIOS reminder scheduling, persistence,
 * exact alarm delivery, snooze, and recovery.
 */
@Singleton
class ReminderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val scheduler: IReminderScheduler,
    private val buddyModeRepository: IBuddyModeRepository,
) : IReminderEngine {

    companion object {
        private const val TAG = "ReminderEngine"
    }

    override suspend fun createReminder(
        title: String,
        description: String,
        triggerTimestamp: Long?,
        recurrenceRule: String?,
        voiceEnabled: Boolean,
    ): ReminderEngineResult {
        val rawTitle = title.trim()
        if (rawTitle.isBlank()) {
            return ReminderEngineResult.Failure("Reminder title cannot be blank.")
        }

        val now = System.currentTimeMillis()

        // ── Diagnostic: log raw incoming timestamp before any adjustment ──
        AppLogger.d(TAG, "createReminder: raw triggerTimestamp=$triggerTimestamp now=$now delta=${(triggerTimestamp ?: 0L) - now}ms title='$rawTitle'")

        val computedTrigger = calculateValidTriggerTime(triggerTimestamp ?: (now + 3600 * 1000L), recurrenceRule)

        // ── Diagnostic: log computed trigger and verify it is in the future ──
        AppLogger.d(TAG, "createReminder: computedTrigger=$computedTrigger delta-from-now=${computedTrigger - now}ms")

        val taskId = UUID.randomUUID().toString()
        val deviceTimezone = TimeZone.getDefault().id

        val task = Task(
            id = taskId,
            title = rawTitle,
            description = description,
            isCompleted = false,
            createdAt = now,
            dueDate = computedTrigger,
            reminderTime = computedTrigger,
            priority = TaskPriority.MEDIUM,
            tags = emptyList(),
            isReminder = true,
            notificationId = taskId.hashCode(),
            timezone = deviceTimezone,
            status = TaskStatus.PENDING,
            recurrenceRule = recurrenceRule,
            deliveryState = ReminderDeliveryState.SCHEDULED,
            voiceEnabled = voiceEnabled,
            notificationEnabled = true,
            morningBriefingEligible = true,
        )

        // ── Diagnostic: log unique alarm identity ──
        AppLogger.d(TAG, "createReminder: taskId=$taskId notificationId=${task.notificationId} (unique PendingIntent requestCode)")

        // 1. Persist to Room Database
        taskDao.insert(task.toEntity())

        // 2. Check Exact Alarm Permission
        if (!scheduler.canScheduleExactAlarms()) {
            AppLogger.w(TAG, "Exact alarms permission missing on Android OS")
            return ReminderEngineResult.PermissionRequired(
                permissionName = "SCHEDULE_EXACT_ALARM",
                message = "AIOS needs Alarms & reminders permission to deliver this reminder at the exact time."
            )
        }

        // 3. Schedule Alarm with OS
        val scheduled = scheduler.schedule(task)
        if (!scheduled) {
            AppLogger.w(TAG, "AlarmManager returned false for task id=$taskId")
            return ReminderEngineResult.Failure("The reminder was saved, but I couldn't schedule its notification.")
        }

        // 4. Verify Task Persisted in DB
        val verified = taskDao.getById(taskId)
        if (verified == null) {
            return ReminderEngineResult.Failure("Could not verify task persistence in database.")
        }

        val timeFormatted = com.buddy.aios.core.common.time.ReminderDateFormatter.formatNaturalDateTime(computedTrigger)
        val confirmationMsg = "Done. I'll remind you $timeFormatted to ${task.title}."

        AppLogger.d(TAG, "Successfully created and verified reminder id=$taskId for $timeFormatted")
        return ReminderEngineResult.Success(task = task, confirmationMessage = confirmationMsg)
    }

    override suspend fun scheduleReminder(task: Task): Boolean {
        if (task.isCompleted || task.status == TaskStatus.CANCELLED) return false
        val scheduled = scheduler.schedule(task)
        if (scheduled) {
            taskDao.updateDeliveryState(task.id, ReminderDeliveryState.SCHEDULED.name)
        }
        return scheduled
    }

    override suspend fun cancelReminder(taskId: String): Boolean {
        val taskEntity = taskDao.getById(taskId) ?: return false
        val task = taskEntity.toDomain()
        scheduler.cancel(task)
        taskDao.updateReminderSchedule(
            id = taskId,
            newReminderTime = 0L,
            newStatus = TaskStatus.CANCELLED.name,
            deliveryState = ReminderDeliveryState.CANCELLED.name,
        )
        AppLogger.d(TAG, "Cancelled reminder task id=$taskId")
        return true
    }

    override suspend fun rescheduleReminder(
        taskId: String,
        newTime: Long,
        repeatRule: String?
    ): ReminderEngineResult {
        val existing = taskDao.getById(taskId)
            ?: return ReminderEngineResult.Failure("Task with ID '$taskId' not found.")

        val now = System.currentTimeMillis()
        val validTime = calculateValidTriggerTime(newTime, repeatRule ?: existing.recurrenceRule)

        // Cancel existing alarm
        scheduler.cancel(taskId, existing.notificationId)

        // Update database
        taskDao.updateReminderSchedule(
            id = taskId,
            newReminderTime = validTime,
            newStatus = TaskStatus.PENDING.name,
            deliveryState = ReminderDeliveryState.SCHEDULED.name,
        )

        val updatedEntity = taskDao.getById(taskId)
            ?: return ReminderEngineResult.Failure("Could not fetch updated task.")
        val updatedTask = updatedEntity.toDomain()

        val scheduled = scheduler.schedule(updatedTask)
        if (!scheduled) {
            return ReminderEngineResult.Failure("Task schedule updated, but OS alarm scheduling failed.")
        }

        val formattedTime = formatLocalTime(validTime, updatedTask.timezone)
        val msg = "Done. I've moved '${updatedTask.title}' to $formattedTime."
        return ReminderEngineResult.Success(updatedTask, msg)
    }

    override suspend fun restoreReminders(): Int {
        val now = System.currentTimeMillis()
        val pendingEntities = taskDao.getPendingReminders()

        var rescheduledCount = 0
        pendingEntities.forEach { entity ->
            val reminderTime = entity.reminderTime ?: 0L
            if (!entity.isCompleted && entity.status != TaskStatus.CANCELLED.name) {
                if (reminderTime > now) {
                    val domainTask = entity.toDomain()
                    val success = scheduler.schedule(domainTask)
                    if (success) rescheduledCount++
                } else {
                    // Past reminder that was missed while device was off/down
                    taskDao.updateDeliveryState(entity.id, ReminderDeliveryState.MISSED.name)
                }
            }
        }
        AppLogger.d(TAG, "Restored $rescheduledCount active reminders after reboot/update check")
        return rescheduledCount
    }

    override suspend fun handleReminderTriggered(taskId: String, occurrenceId: Int): Boolean {
        val taskEntity = taskDao.getById(taskId) ?: run {
            AppLogger.w(TAG, "Triggered task id=$taskId not found in DB")
            return false
        }

        if (taskEntity.isCompleted || taskEntity.status == TaskStatus.CANCELLED.name) {
            AppLogger.d(TAG, "Triggered task id=$taskId is completed or cancelled — ignoring")
            return false
        }

        val buddyMode = buddyModeRepository.getBuddyMode()
        if (buddyMode == BuddyMode.OFF) {
            AppLogger.d(TAG, "BuddyMode is OFF — suppressing reminder notification for task id=$taskId")
            return false
        }

        taskDao.updateDeliveryState(taskId, ReminderDeliveryState.TRIGGERED.name)

        // Handle recurring next occurrence calculation
        val recurrenceRule = taskEntity.recurrenceRule
        if (!recurrenceRule.isNullOrEmpty()) {
            val now = System.currentTimeMillis()
            val nextTrigger = calculateNextRecurringTime(now, recurrenceRule)
            if (nextTrigger > now) {
                taskDao.updateReminderSchedule(
                    id = taskId,
                    newReminderTime = nextTrigger,
                    newStatus = TaskStatus.PENDING.name,
                    deliveryState = ReminderDeliveryState.SCHEDULED.name,
                )
                val updatedEntity = taskDao.getById(taskId)
                if (updatedEntity != null) {
                    scheduler.schedule(updatedEntity.toDomain())
                    AppLogger.d(TAG, "Rescheduled next recurring occurrence for task id=$taskId at $nextTrigger")
                }
            }
        } else {
            // ONE_TIME Reminder: When triggered & delivered, mark completed & archive out of active tasks!
            taskDao.markCompleted(taskId, true)
            taskDao.updateReminderSchedule(taskId, 0L, TaskStatus.COMPLETED.name, ReminderDeliveryState.DELIVERED.name)
            AppLogger.d(TAG, "Triggered ONE_TIME reminder id=$taskId — marked completed and archived from active tasks")
        }

        return true
    }

    override suspend fun completeReminder(taskId: String): Boolean {
        val taskEntity = taskDao.getById(taskId) ?: return false
        val recurrenceRule = taskEntity.recurrenceRule

        scheduler.cancel(taskId, taskEntity.notificationId)

        if (recurrenceRule.isNullOrBlank()) {
            // ONE_TIME Reminder: Mark completed and archive out of active reminders list
            taskDao.markCompleted(taskId, true)
            taskDao.updateReminderSchedule(taskId, 0L, TaskStatus.COMPLETED.name, ReminderDeliveryState.DELIVERED.name)
            AppLogger.d(TAG, "Completed ONE_TIME reminder id=$taskId — archived from active reminders")
        } else {
            // RECURRING / PERMANENT Reminder: Today's occurrence completed, calculate and schedule next occurrence!
            val now = System.currentTimeMillis()
            val nextTrigger = calculateNextRecurringTime(now, recurrenceRule)

            taskDao.updateReminderSchedule(
                id = taskId,
                newReminderTime = nextTrigger,
                newStatus = TaskStatus.PENDING.name,
                deliveryState = ReminderDeliveryState.SCHEDULED.name,
            )

            val updatedEntity = taskDao.getById(taskId)
            if (updatedEntity != null) {
                scheduler.schedule(updatedEntity.toDomain())
                AppLogger.d(TAG, "Completed recurring occurrence for id=$taskId — scheduled next occurrence at $nextTrigger")
            }
        }
        return true
    }

    override suspend fun snoozeReminder(taskId: String, snoozeMinutes: Int): ReminderEngineResult {
        val existing = taskDao.getById(taskId)
            ?: return ReminderEngineResult.Failure("Task with ID '$taskId' not found.")

        val mins = if (snoozeMinutes <= 0) 10 else snoozeMinutes
        val snoozeTime = System.currentTimeMillis() + (mins * 60 * 1000L)

        scheduler.cancel(taskId, existing.notificationId)

        taskDao.updateReminderSchedule(
            id = taskId,
            newReminderTime = snoozeTime,
            newStatus = TaskStatus.SNOOZED.name,
            deliveryState = ReminderDeliveryState.SNOOZED.name,
        )

        val updatedEntity = taskDao.getById(taskId)
            ?: return ReminderEngineResult.Failure("Failed to retrieve snoozed task.")
        val domainTask = updatedEntity.toDomain()

        val scheduled = scheduler.schedule(domainTask)
        if (!scheduled) {
            return ReminderEngineResult.Failure("Snoozed in database, but alarm scheduling failed.")
        }

        val msg = "Okay, I'll remind you again in $mins minute${if (mins > 1) "s" else ""}."
        return ReminderEngineResult.Success(domainTask, msg)
    }

    // ── Helper Time Methods ──────────────────────────────────────────────────

    private fun calculateValidTriggerTime(requestedTime: Long, repeatRule: String?): Long {
        val now = System.currentTimeMillis()
        if (requestedTime > now) return requestedTime

        // Past time protection rule: if requested time has passed today, move to next occurrence!
        val cal = Calendar.getInstance().apply { timeInMillis = requestedTime }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }

        cal.set(Calendar.YEAR, nowCal.get(Calendar.YEAR))
        cal.set(Calendar.DAY_OF_YEAR, nowCal.get(Calendar.DAY_OF_YEAR))

        if (cal.timeInMillis <= now) {
            when (repeatRule?.uppercase()) {
                "WEEKDAYS" -> {
                    do {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    } while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                }
                "WEEKLY" -> cal.add(Calendar.DAY_OF_YEAR, 7)
                else -> cal.add(Calendar.DAY_OF_YEAR, 1) // Daily / One-time next day
            }
        }
        return cal.timeInMillis
    }

    private fun calculateNextRecurringTime(fromTime: Long, rule: String): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromTime }
        when (rule.uppercase()) {
            "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> cal.add(Calendar.DAY_OF_YEAR, 7)
            "WEEKDAYS" -> {
                do {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                } while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            }
            else -> cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun formatLocalTime(timestamp: Long, timezoneId: String): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("EEEE 'at' h:mm a", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone(timezoneId)
        }
        return format.format(date)
    }
}
