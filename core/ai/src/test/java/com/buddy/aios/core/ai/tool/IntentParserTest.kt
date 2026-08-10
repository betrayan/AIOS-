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
}
