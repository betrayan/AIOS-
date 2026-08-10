package com.buddy.aios.core.ai.voice

import com.buddy.aios.core.ai.engine.AIChunk
import com.buddy.aios.core.ai.engine.AIEngine
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.canVoiceInput
import com.buddy.aios.core.domain.entity.canVoiceOutput
import com.buddy.aios.core.domain.result.Result
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
    fun `Test 2 - Full response AI explanation considers entire answer`() = runTest {
        val longResponse = "Python is a high-level programming language. It supports multiple paradigms. Python is widely used for web development, automation, data science, machine learning, and scripting. Its biggest advantages are readability and a massive library ecosystem."
        val input = VoiceResponseProcessorInput(
            userMessage = "What is Python?",
            fullResponse = longResponse,
        )

        coEvery { aiEngine.complete(any()) } returns flowOf(
            Result.Success(AIChunk(text = "Python is a versatile programming language popular for web development, data science, and AI because of its simple syntax and rich library ecosystem.", isComplete = true))
        )

        val result = processor.process(input)

        assertTrue(result.isSummarized)
        assertTrue(result.text.contains("Python is a versatile programming language"))
    }

    @Test
    fun `Test 3 - Full context local fallback includes start middle and conclusion`() {
        val longText = "Section 1 starts here. Section 2 is the middle body. Section 3 is the final conclusion."
        val fullSummary = processor.extractFullContextLocalExplanation(longText)

        assertTrue(fullSummary.contains("Section 1 starts here."))
        assertTrue(fullSummary.contains("Section 2 is the middle body."))
        assertTrue(fullSummary.contains("Section 3 is the final conclusion."))
    }

    @Test
    fun `Test 4 - Code blocks are not read as code`() = runTest {
        val codeResponse = "Here is your solution:\n```python\ndef hello():\n    print('Hello World')\n```\nHope that helps!"
        val input = VoiceResponseProcessorInput(
            userMessage = "Write a hello world in Python",
            fullResponse = codeResponse,
        )

        val result = processor.process(input)

        assertFalse(result.text.contains("def hello():"))
        assertFalse(result.text.contains("```"))
        assertTrue(result.text.contains("I've written the python solution for you on screen."))
    }

    @Test
    fun `Test 5 - Directives and raw JSON stripped from speech`() {
        val textWithDirective = "Done! [BUDDY_ACTION:{\"tool\":\"TASK\",\"action\":\"CREATE\",\"title\":\"Study Java\"}] I have created your task."
        val cleaned = processor.cleanTextForSpeech(textWithDirective)

        assertFalse(cleaned.contains("BUDDY_ACTION"))
        assertFalse(cleaned.contains("tool"))
        assertTrue(cleaned.contains("Done! I have created your task."))
    }

    @Test
    fun `Test 6 - URLs stripped from speech`() {
        val textWithUrl = "Visit https://google.com for more info."
        val cleaned = processor.cleanTextForSpeech(textWithUrl)

        assertFalse(cleaned.contains("https://google.com"))
        assertTrue(cleaned.contains("Visit for more info."))
    }

    @Test
    fun `Test 7 - BuddyMode OFF prevents voice input and output`() {
        val mode = BuddyMode.OFF
        assertFalse(mode.canVoiceInput)
        assertFalse(mode.canVoiceOutput)
    }

    @Test
    fun `Test 8 - BuddyMode SILENT prevents voice input and output`() {
        val mode = BuddyMode.SILENT
        assertFalse(mode.canVoiceInput)
        assertFalse(mode.canVoiceOutput)
    }

    @Test
    fun `Test 9 - BuddyMode QUIET permits user-initiated voice`() {
        val mode = BuddyMode.QUIET
        assertTrue(mode.canVoiceInput)
        assertTrue(mode.canVoiceOutput)
    }

    @Test
    fun `Test 10 - BuddyMode ACTIVE permits full voice interaction`() {
        val mode = BuddyMode.ACTIVE
        assertTrue(mode.canVoiceInput)
        assertTrue(mode.canVoiceOutput)
    }
}
