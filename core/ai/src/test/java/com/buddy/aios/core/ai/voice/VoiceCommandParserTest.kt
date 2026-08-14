package com.buddy.aios.core.ai.voice

import com.buddy.aios.core.domain.entity.BuddyMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VoiceCommandParserTest {

    private lateinit var parser: VoiceCommandParser

    @BeforeEach
    fun setUp() {
        parser = VoiceCommandParser()
    }

    @Test
    fun `TEST 1 - Voice Stop commands`() {
        assertEquals(VoiceCommand.StopListening, parser.parse("Stop listening"))
        assertEquals(VoiceCommand.StopListening, parser.parse("turn off microphone"))
        assertEquals(VoiceCommand.StopListening, parser.parse("stop voice mode"))
    }

    @Test
    fun `TEST 2 - Continuous Voice Mode commands`() {
        assertEquals(VoiceCommand.SetVoiceMode(true), parser.parse("Enable continuous voice"))
        assertEquals(VoiceCommand.SetVoiceMode(false), parser.parse("Turn off voice mode"))
    }

    @Test
    fun `TEST 3 - Buddy Mode commands`() {
        val cmd1 = parser.parse("Switch to quiet mode")
        assertNotNull(cmd1)
        assertTrue(cmd1 is VoiceCommand.SetBuddyModeCommand)
        assertEquals(BuddyMode.QUIET, (cmd1 as VoiceCommand.SetBuddyModeCommand).mode)

        val cmd2 = parser.parse("Switch to active mode")
        assertEquals(BuddyMode.ACTIVE, (cmd2 as VoiceCommand.SetBuddyModeCommand).mode)
    }

    @Test
    fun `TEST 4 - Voice Recording commands`() {
        assertEquals(VoiceCommand.RecordingCommand.Start, parser.parse("Start recording"))
        assertEquals(VoiceCommand.RecordingCommand.Stop, parser.parse("Stop recording"))
        assertEquals(VoiceCommand.RecordingCommand.Save, parser.parse("Save this recording"))
        assertEquals(VoiceCommand.RecordingCommand.Delete, parser.parse("Delete this recording"))
    }

    @Test
    fun `TEST 5 - Summary depth commands`() {
        assertEquals(VoiceCommand.SummaryCommand.ShortSummary, parser.parse("Summarize that"))
        assertEquals(VoiceCommand.SummaryCommand.ExplainSimply, parser.parse("Explain it simply"))
        assertEquals(VoiceCommand.SummaryCommand.DetailedExplanation, parser.parse("Explain it in detail"))
    }
}
