package com.buddy.aios.core.common.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar

class ReminderSynchronizationTest {

    @Test
    fun `Test 1 - Tomorrow morning 6 40 AM parses exact date and time`() {
        val now = System.currentTimeMillis()
        val result = NaturalLanguageTimeParser.parse("remind me tomorrow morning 6:40 am for a flower market", now)

        assertNotNull(result.timestamp)
        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }

        val tomorrowCal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
        }

        assertEquals(tomorrowCal.get(Calendar.YEAR), cal.get(Calendar.YEAR))
        assertEquals(tomorrowCal.get(Calendar.DAY_OF_YEAR), cal.get(Calendar.DAY_OF_YEAR))
        assertEquals(6, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(40, cal.get(Calendar.MINUTE))

        val formatted = ReminderDateFormatter.formatDueDateTime(result.timestamp, now)
        assertEquals("Due tomorrow at 6:40 AM", formatted)
    }

    @Test
    fun `Test 2 - Today at 7 30 PM parses exact date and time`() {
        val morningNow = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        val result = NaturalLanguageTimeParser.parse("remind me today at 7:30 PM to study Java", morningNow)

        assertNotNull(result.timestamp)
        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        val nowCal = Calendar.getInstance().apply { timeInMillis = morningNow }

        assertEquals(nowCal.get(Calendar.YEAR), cal.get(Calendar.YEAR))
        assertEquals(19, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))

        val formatted = ReminderDateFormatter.formatDueDateTime(result.timestamp, morningNow)
        assertEquals("Due today at 7:30 PM", formatted)
    }

    @Test
    fun `Test 3 - Monday at 8 15 AM parses exact day of week and minute`() {
        val now = System.currentTimeMillis()
        val result = NaturalLanguageTimeParser.parse("remind me Monday at 8:15 AM to call someone", now)

        assertNotNull(result.timestamp)
        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }

        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, cal.get(Calendar.MINUTE))

        val formatted = ReminderDateFormatter.formatDueDateTime(result.timestamp, now)
        assertTrue(formatted.contains("8:15 AM"))
    }

    @Test
    fun `Test 4 - NaturalLanguageTimeParser preserves title text without date snippets`() {
        val now = System.currentTimeMillis()
        val result = NaturalLanguageTimeParser.parse("tomorrow morning 6:40 am flower market", now)

        assertEquals("flower market", result.cleanedText)
    }
}
