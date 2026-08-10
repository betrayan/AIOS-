package com.buddy.aios.core.domain.entity

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuddyCapabilityPolicyTest {

    @Test
    fun `ACTIVE mode allows proactive conversation, voice, text, tasks, and background AI`() {
        val capabilities = BuddyMode.ACTIVE.getCapabilities()

        assertTrue(capabilities.allowProactiveConversation)
        assertTrue(capabilities.allowVoiceInputOutput)
        assertTrue(capabilities.allowAiBackgroundProcessing)
        assertTrue(capabilities.allowUserRemindersAndTasks)
        assertTrue(capabilities.allowTextInteraction)
    }

    @Test
    fun `QUIET mode disables proactive conversation but allows text, voice, tasks, and background processing`() {
        val capabilities = BuddyMode.QUIET.getCapabilities()

        assertFalse(capabilities.allowProactiveConversation)
        assertTrue(capabilities.allowVoiceInputOutput)
        assertTrue(capabilities.allowAiBackgroundProcessing)
        assertTrue(capabilities.allowUserRemindersAndTasks)
        assertTrue(capabilities.allowTextInteraction)
    }

    @Test
    fun `SILENT mode disables proactive conversation and voice but allows text, tasks, and background processing`() {
        val capabilities = BuddyMode.SILENT.getCapabilities()

        assertFalse(capabilities.allowProactiveConversation)
        assertFalse(capabilities.allowVoiceInputOutput)
        assertTrue(capabilities.allowAiBackgroundProcessing)
        assertTrue(capabilities.allowUserRemindersAndTasks)
        assertTrue(capabilities.allowTextInteraction)
    }

    @Test
    fun `OFF mode halts AI background processing, proactive, voice, and text, while preserving critical tasks`() {
        val capabilities = BuddyMode.OFF.getCapabilities()

        assertFalse(capabilities.allowProactiveConversation)
        assertFalse(capabilities.allowVoiceInputOutput)
        assertFalse(capabilities.allowAiBackgroundProcessing)
        assertFalse(capabilities.allowTextInteraction)
        assertTrue(capabilities.allowUserRemindersAndTasks)
    }
}
