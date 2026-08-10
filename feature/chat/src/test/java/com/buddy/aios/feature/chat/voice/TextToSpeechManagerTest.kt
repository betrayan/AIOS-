package com.buddy.aios.feature.chat.voice

import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.canVoiceOutput
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextToSpeechManagerTest {

    @Test
    fun `test ACTIVE mode permits voice output`() {
        val mode = BuddyMode.ACTIVE
        assertTrue(mode.canVoiceOutput)
    }

    @Test
    fun `test QUIET mode permits voice output`() {
        val mode = BuddyMode.QUIET
        assertTrue(mode.canVoiceOutput)
    }

    @Test
    fun `test SILENT mode blocks voice output`() {
        val mode = BuddyMode.SILENT
        assertFalse(mode.canVoiceOutput)
    }

    @Test
    fun `test OFF mode blocks voice output`() {
        val mode = BuddyMode.OFF
        assertFalse(mode.canVoiceOutput)
    }
}
