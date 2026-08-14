package com.buddy.aios.core.common.time

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Single source of truth for formatting task and reminder due dates & times.
 *
 * All displays (Home Screen Top Priority card, Chat confirmation, Notifications,
 * Morning Briefing) use this canonical formatter.
 */
object ReminderDateFormatter {

    /**
     * Formats [timestamp] into a human-friendly due string relative to [now].
     *
     * Examples:
     * - Today 06:40 AM -> "Due today at 6:40 AM"
     * - Tomorrow 06:40 AM -> "Due tomorrow at 6:40 AM"
     * - Next Friday 06:40 AM -> "Due Friday at 6:40 AM"
     * - Aug 20 06:40 AM -> "Due Aug 20 at 6:40 AM"
     */
    fun formatDueDateTime(timestamp: Long?, now: Long = System.currentTimeMillis()): String {
        if (timestamp == null || timestamp <= 0L) {
            return "Time not set"
        }

        val targetCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }

        val timeStr = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(timestamp))

        val targetYear = targetCal.get(Calendar.YEAR)
        val targetDay = targetCal.get(Calendar.DAY_OF_YEAR)

        val nowYear = nowCal.get(Calendar.YEAR)
        val nowDay = nowCal.get(Calendar.DAY_OF_YEAR)

        val isSameYear = targetYear == nowYear

        if (isSameYear && targetDay == nowDay) {
            return "Due today at $timeStr"
        }

        val tomorrowCal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
        }
        if (targetYear == tomorrowCal.get(Calendar.YEAR) && targetDay == tomorrowCal.get(Calendar.DAY_OF_YEAR)) {
            return "Due tomorrow at $timeStr"
        }

        // Check if within the next 6 days
        val diffDays = (timestamp - now) / (1000 * 60 * 60 * 24L)
        if (diffDays in 2..6) {
            val dayOfWeekStr = SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date(timestamp))
            return "Due $dayOfWeekStr at $timeStr"
        }

        // Farther out
        val dateStr = SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(timestamp))
        return "Due $dateStr at $timeStr"
    }

    /**
     * Formats [timestamp] into a short natural string without "Due " prefix.
     *
     * Examples:
     * - Today 06:40 AM -> "Today at 6:40 AM"
     * - Tomorrow 06:40 AM -> "Tomorrow at 6:40 AM"
     */
    fun formatNaturalDateTime(timestamp: Long?, now: Long = System.currentTimeMillis()): String {
        val full = formatDueDateTime(timestamp, now)
        return if (full.startsWith("Due ")) full.substring(4) else full
    }
}
