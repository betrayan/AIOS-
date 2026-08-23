package com.buddy.aios.core.ai.tool

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IntentParserTest {

    @Test
    fun `parse returns null tool when no directive is present`() {
        val text = "Hello there! How can I help you today?"
        val result = IntentParser.parse(text)
        assertEquals(text, result.cleanedText)
        assertNull(result.tool)
    }

    @Test
    fun `parse extracts CreateTask tool and strips directive`() {
        val raw = "Sure! I've set a reminder for you.\n[BUDDY_ACTION:{\"tool\":\"TASK\",\"action\":\"CREATE\",\"title\":\"Study Java at 7 PM\",\"dueTimestamp\":null}]"
        val result = IntentParser.parse(raw)

        assertEquals("Sure! I've set a reminder for you.", result.cleanedText)
        assertNotNull(result.tool)

        val tool = result.tool as BuddyTool.CreateTask
        assertEquals("Study Java at 7 PM", tool.title)
    }

    @Test
    fun `parse extracts SaveMemory tool correctly`() {
        val raw = "Got it! I will remember your preference.\n[BUDDY_ACTION:{\"tool\":\"MEMORY\",\"action\":\"SAVE\",\"content\":\"Prefers studying in the morning\",\"importance\":0.8}]"
        val result = IntentParser.parse(raw)

        assertEquals("Got it! I will remember your preference.", result.cleanedText)
        assertNotNull(result.tool)

        val tool = result.tool as BuddyTool.SaveMemory
        assertEquals("Prefers studying in the morning", tool.content)
        assertEquals(0.8f, tool.importance)
    }

    @Test
    fun `parse resolves relative timestamp in title when dueTimestamp is null`() {
        val raw = "Setting reminder...\n[BUDDY_ACTION:{\"tool\":\"TASK\",\"action\":\"CREATE\",\"title\":\"Test AIOS reminder in 2 minutes\",\"dueTimestamp\":null}]"
        val now = System.currentTimeMillis()
        val result = IntentParser.parse(raw)
        val tool = result.tool as BuddyTool.CreateTask

        assertNotNull(tool.dueTimestamp)
        assertTrue(tool.dueTimestamp!! >= now + 100_000L, "dueTimestamp should be ~2 minutes in the future")
    }

    @Test
    fun `parse falls back to natural time parser when dueTimestamp is expired in past`() {
        val expiredSec = 1771497120L // Feb 19, 2026 in seconds
        val raw = "Setting reminder...\n[BUDDY_ACTION:{\"tool\":\"TASK\",\"action\":\"CREATE\",\"title\":\"Test AIOS reminder in 2 minutes\",\"dueTimestamp\":$expiredSec}]"
        val now = System.currentTimeMillis()
        val result = IntentParser.parse(raw)
        val tool = result.tool as BuddyTool.CreateTask

        assertNotNull(tool.dueTimestamp)
        assertTrue(tool.dueTimestamp!! >= now + 100_000L, "dueTimestamp should be resolved relative to current device time")
    }

    @Test
    fun `parse extracts ConfigureMorningWish tool correctly`() {
        val raw = "I've updated your Morning Wish alarm.\n[BUDDY_ACTION:{\"tool\":\"MORNING_WISH\",\"action\":\"SET\",\"hour\":6,\"minute\":0,\"enabled\":true}]"
        val result = IntentParser.parse(raw)

        assertEquals("I've updated your Morning Wish alarm.", result.cleanedText)
        assertNotNull(result.tool)

        val tool = result.tool as BuddyTool.ConfigureMorningWish
        assertEquals(6, tool.hour)
        assertEquals(0, tool.minute)
        assertTrue(tool.isEnabled)
    }
}
