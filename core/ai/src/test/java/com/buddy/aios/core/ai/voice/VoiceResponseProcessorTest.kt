package com.buddy.aios.core.ai.voice

import com.buddy.aios.core.ai.engine.AIEngine
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.canVoiceInput
import com.buddy.aios.core.domain.entity.canVoiceOutput
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VoiceResponseProcessorTest {

    private val aiEngine: AIEngine = mockk(relaxed = true)
    private lateinit var processor: VoiceResponseProcessor

    @BeforeEach
    fun setUp() {
        processor = VoiceResponseProcessor(aiEngine)
    }

    @Test
    fun `Test 1 - Short response uses original text directly`() = runTest {
        val shortResponse = "Sure, I'll do that."
        val input = VoiceResponseProcessorInput(
            userMessage = "Can you help me?",
            fullResponse = shortResponse,
        )

        val result = processor.process(input)

        assertEquals("Sure, I'll do that.", result.text)
        assertFalse(result.isSummarized)
    }

    @Test
    fun `Test 2 - Markdown cleaned properly`() {
        val markdownText = "# Header\nThis is **bold** and *italic* text with [Link](https://example.com)."
        val cleaned = processor.cleanTextForSpeech(markdownText)

        assertFalse(cleaned.contains("#"))
        assertFalse(cleaned.contains("**"))
        assertFalse(cleaned.contains("https://example.com"))
        assertTrue(cleaned.contains("This is bold and italic text with Link."))
    }

    @Test
    fun `Test 3 - Code blocks replaced with conversational code notification`() {
        val codeResponse = "Here is your solution:\n```python\ndef hello():\n    print('Hello World')\n```\nHope that helps!"
        val cleaned = processor.cleanTextForSpeech(codeResponse)

        assertFalse(cleaned.contains("def hello():"))
        assertFalse(cleaned.contains("```"))
        assertTrue(cleaned.contains("I've written the python snippet for you."))
    }

    @Test
    fun `Test 4 - Directives and raw JSON stripped from speech`() {
        val textWithDirective = "Done! [BUDDY_ACTION:{\"tool\":\"TASK\",\"action\":\"CREATE\",\"title\":\"Study Java\"}] I have created your task."
        val cleaned = processor.cleanTextForSpeech(textWithDirective)

        assertFalse(cleaned.contains("BUDDY_ACTION"))
        assertFalse(cleaned.contains("tool"))
        assertTrue(cleaned.contains("Done! I have created your task."))
    }

    @Test
    fun `Test 5 - URLs stripped from speech`() {
        val textWithUrl = "Visit https://google.com for more info."
        val cleaned = processor.cleanTextForSpeech(textWithUrl)

        assertFalse(cleaned.contains("https://google.com"))
        assertTrue(cleaned.contains("Visit for more info."))
    }

    @Test
    fun `Test 6 - Local summary fallback extracts first sentences`() {
        val longText = "Docker is a containerization platform. It packages applications into isolated containers. Containers share the host kernel. Images are immutable templates."
        val summary = processor.extractLocalSummaryFallback(longText)

        assertTrue(summary.length <= VoiceResponseProcessor.SHORT_RESPONSE_THRESHOLD)
        assertTrue(summary.contains("Docker is a containerization platform."))
    }

    @Test
    fun `Test 7 - Long text uses local smart summarization zero network overhead`() = runTest {
        val longResponse = "Docker is a containerization platform that allows developers to package applications and their dependencies into isolated containers. Containers share the host OS kernel while maintaining process isolation. Docker images are immutable templates used to create containers across different development environments."
        val input = VoiceResponseProcessorInput(
            userMessage = "Explain Docker",
            fullResponse = longResponse,
        )

        val result = processor.process(input)

        assertTrue(result.isSummarized)
        assertTrue(result.text.contains("Docker is a containerization platform"))
        assertTrue(result.text.length <= VoiceResponseProcessor.SHORT_RESPONSE_THRESHOLD)
    }

    @Test
    fun `Test 8 - BuddyMode OFF prevents voice input and output`() {
        val mode = BuddyMode.OFF
        assertFalse(mode.canVoiceInput)
        assertFalse(mode.canVoiceOutput)
    }

    @Test
    fun `Test 9 - BuddyMode SILENT prevents voice input and output`() {
        val mode = BuddyMode.SILENT
        assertFalse(mode.canVoiceInput)
        assertFalse(mode.canVoiceOutput)
    }

    @Test
    fun `Test 10 - BuddyMode QUIET permits user-initiated voice`() {
        val mode = BuddyMode.QUIET
        assertTrue(mode.canVoiceInput)
        assertTrue(mode.canVoiceOutput)
    }

    @Test
    fun `Test 11 - BuddyMode ACTIVE permits full voice interaction`() {
        val mode = BuddyMode.ACTIVE
        assertTrue(mode.canVoiceInput)
        assertTrue(mode.canVoiceOutput)
    }
}
