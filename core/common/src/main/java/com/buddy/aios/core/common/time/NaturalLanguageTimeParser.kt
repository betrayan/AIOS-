package com.buddy.aios.core.common.time

import java.util.Calendar
import java.util.Locale

/**
 * Deterministic natural language date & time parser.
 *
 * Supported formats:
 * - "tomorrow morning 6:40 am", "tomorrow at 6:40 AM", "at 6:40 am tomorrow" -> Tomorrow 06:40 AM
 * - "today at 7:30 PM", "7:30 PM today" -> Today 19:30
 * - "tonight at 9", "tonight at 9 PM" -> Today 21:00
 * - "Monday at 8:15 AM", "next Friday at 10:30 AM" -> Target day 08:15 AM / 10:30 AM
 * - "tomorrow morning" (no time) -> Tomorrow 08:00 AM
 * - "tomorrow evening" (no time) -> Tomorrow 18:00
 * - "in 30 mins", "in 2 hours" -> Relative timestamp
 */
object NaturalLanguageTimeParser {

    data class ParsedTimeResult(
        val timestamp: Long?,
        val cleanedText: String,
    )

    fun parse(rawText: String, now: Long = System.currentTimeMillis()): ParsedTimeResult {
        var text = rawText.trim()
        val lower = text.lowercase(Locale.ENGLISH)

        // 1. Relative Time Check ("in X mins" / "in X hours")
        val relativeMinMatch = Regex("(?i)\\b(in)\\s+(\\d+)\\s*(minute|minutes|min|mins)\\b").find(text)
        if (relativeMinMatch != null) {
            val mins = relativeMinMatch.groupValues[2].toLongOrNull() ?: 1L
            val ts = now + (mins * 60 * 1000L)
            val cleaned = text.replace(relativeMinMatch.value, "").trim()
            return ParsedTimeResult(ts, cleaned)
        }

        val relativeHourMatch = Regex("(?i)\\b(in)\\s+(\\d+)\\s*(hour|hours|hr|hrs)\\b").find(text)
        if (relativeHourMatch != null) {
            val hrs = relativeHourMatch.groupValues[2].toLongOrNull() ?: 1L
            val ts = now + (hrs * 3600 * 1000L)
            val cleaned = text.replace(relativeHourMatch.value, "").trim()
            return ParsedTimeResult(ts, cleaned)
        }

        // 2. Explicit Time extraction: "6:40 AM", "6:40AM", "7:30 PM", "9 PM", "9PM", "8:15"
        val explicitTimeRegex = Regex("(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b")
        val timeMatches = explicitTimeRegex.findAll(text).toList()

        var hour: Int? = null
        var minute: Int? = null
        var matchedTimeSnippet: String? = null

        for (match in timeMatches) {
            val h = match.groupValues[1].toIntOrNull() ?: continue
            val m = match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
            val amPm = match.groupValues[3].lowercase(Locale.ENGLISH)

            // Validate hour
            if (h in 1..24) {
                var calculatedHour = h
                if (amPm == "pm" && calculatedHour < 12) calculatedHour += 12
                if (amPm == "am" && calculatedHour == 12) calculatedHour = 0

                // If no am/pm specified and hour <= 12, check context (e.g. "tonight at 9" -> 21)
                if (amPm.isBlank()) {
                    if (lower.contains("tonight") || lower.contains("evening") || lower.contains("night")) {
                        if (calculatedHour < 12) calculatedHour += 12
                    } else if (lower.contains("morning")) {
                        if (calculatedHour == 12) calculatedHour = 0
                    }
                }

                hour = calculatedHour
                minute = m
                matchedTimeSnippet = match.value
                break
            }
        }

        // 3. Date Determination
        val cal = Calendar.getInstance().apply { timeInMillis = now }

        val isTomorrow = lower.contains("tomorrow")
        val isTonight = lower.contains("tonight")
        val isToday = lower.contains("today") || isTonight

        var matchedDateSnippet = ""

        if (isTomorrow) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            matchedDateSnippet = "tomorrow"
        } else {
            // Day of week check ("Monday", "next Friday", etc.)
            val daysOfWeek = mapOf(
                "monday" to Calendar.MONDAY,
                "tuesday" to Calendar.TUESDAY,
                "wednesday" to Calendar.WEDNESDAY,
                "thursday" to Calendar.THURSDAY,
                "friday" to Calendar.FRIDAY,
                "saturday" to Calendar.SATURDAY,
                "sunday" to Calendar.SUNDAY
            )

            for ((dayName, targetDayOfWeek) in daysOfWeek) {
                if (lower.contains(dayName)) {
                    val currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                    var daysUntil = targetDayOfWeek - currentDayOfWeek
                    if (daysUntil <= 0) daysUntil += 7
                    cal.add(Calendar.DAY_OF_YEAR, daysUntil)
                    matchedDateSnippet = dayName
                    break
                }
            }
        }

        // 4. Time Assignment
        if (hour != null && minute != null) {
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            // If time today has passed, roll to tomorrow
            if (!isTomorrow && matchedDateSnippet.isEmpty() && cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            var cleaned = text
            if (matchedTimeSnippet != null) cleaned = cleaned.replace(matchedTimeSnippet, "")
            if (matchedDateSnippet.isNotBlank()) cleaned = cleaned.replace(Regex("(?i)\\b$matchedDateSnippet\\b"), "")
            cleaned = cleaned.replace(Regex("(?i)\\b(morning|evening|afternoon|night|tonight|at|on|for)\\b"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

            return ParsedTimeResult(cal.timeInMillis, cleaned)
        }

        // 5. Broad Time Windows (e.g. "tomorrow morning" without explicit time)
        if (isTomorrow || isToday || matchedDateSnippet.isNotEmpty()) {
            val defaultHour = when {
                lower.contains("morning") -> 8
                lower.contains("afternoon") -> 14
                lower.contains("evening") -> 18
                lower.contains("tonight") || lower.contains("night") -> 21
                else -> 9
            }
            cal.set(Calendar.HOUR_OF_DAY, defaultHour)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            if (cal.timeInMillis <= now && matchedDateSnippet.isEmpty()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            var cleaned = text.replace(Regex("(?i)\\b(tomorrow|today|tonight|morning|evening|afternoon|night|at|on|for)\\b"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

            return ParsedTimeResult(cal.timeInMillis, cleaned)
        }

        return ParsedTimeResult(null, text)
    }
}
