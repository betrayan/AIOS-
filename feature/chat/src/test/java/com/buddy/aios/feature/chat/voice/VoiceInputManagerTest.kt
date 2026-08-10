package com.buddy.aios.feature.chat.voice

import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.canVoiceInput
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VoiceInputManagerTest {

    private val buddyModeRepository: IBuddyModeRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        coEvery { buddyModeRepository.observeBuddyMode() } returns flowOf(BuddyMode.ACTIVE)
    }

    @Test
    fun `test ACTIVE mode allows voice input`() {
        val mode = BuddyMode.ACTIVE
        assertTrue(mode.canVoiceInput)
    }

    @Test
    fun `test QUIET mode allows voice input`() {
        val mode = BuddyMode.QUIET
        assertTrue(mode.canVoiceInput)
    }

    @Test
    fun `test SILENT mode blocks voice input`() {
        val mode = BuddyMode.SILENT
        assertFalse(mode.canVoiceInput)
    }

    @Test
    fun `test OFF mode blocks voice input`() {
        val mode = BuddyMode.OFF
        assertFalse(mode.canVoiceInput)
    }
}
