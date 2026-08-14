package com.buddy.aios.workers.notification

import com.buddy.aios.core.common.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

data class ReminderTimingAudit(
    val reminderId: String,
    val occurrenceId: Int,
    val requestedAt: Long,
    val scheduledAt: Long,
    val triggeredAt: Long,
    val differenceMs: Long,
    val differenceSeconds: Long,
    val isDelayed: Boolean,
)

/**
 * Monitors reminder trigger timestamps against target scheduled times.
 * Flags delivery delays without shifting or corrupting the underlying logical reminder schedule.
 */
@Singleton
class ReminderTimingMonitor @Inject constructor() {

    companion object {
        private const val TAG = "ReminderTimingMonitor"
        private const val DELAY_THRESHOLD_SECONDS = 60L
    }

    private val auditLogs = mutableListOf<ReminderTimingAudit>()

    fun recordTrigger(
        reminderId: String,
        occurrenceId: Int,
        requestedAt: Long,
        scheduledAt: Long,
        triggeredAt: Long = System.currentTimeMillis()
    ): ReminderTimingAudit {
        val diffMs = triggeredAt - scheduledAt
        val diffSec = diffMs / 1000L
        val isDelayed = diffSec > DELAY_THRESHOLD_SECONDS

        val audit = ReminderTimingAudit(
            reminderId = reminderId,
            occurrenceId = occurrenceId,
            requestedAt = requestedAt,
            scheduledAt = scheduledAt,
            triggeredAt = triggeredAt,
            differenceMs = diffMs,
            differenceSeconds = diffSec,
            isDelayed = isDelayed
        )

        auditLogs.add(audit)

        if (isDelayed) {
            AppLogger.w(
                TAG,
                "FLAG_DELAYED: Reminder id=$reminderId triggered $diffSec seconds late (scheduled=$scheduledAt, triggered=$triggeredAt). Schedule remains unaltered."
            )
        } else {
            AppLogger.d(
                TAG,
                "Reminder id=$reminderId delivered on schedule (variance=${diffSec}s)."
            )
        }

        return audit
    }

    fun getAuditHistory(): List<ReminderTimingAudit> = auditLogs.toList()
}
