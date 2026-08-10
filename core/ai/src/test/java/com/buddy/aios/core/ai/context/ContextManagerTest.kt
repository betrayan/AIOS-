package com.buddy.aios.core.ai.context

import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.entity.MessageRole
import com.buddy.aios.core.domain.entity.UserProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContextManagerTest {

    private lateinit var contextManager: ContextManager

    @BeforeEach
    fun setUp() {
        contextManager = ContextManager()
    }

    @Test
    fun `estimateTokens returns reasonable estimate`() {
        val text = "Hello world this is a test string"
        val tokens = contextManager.estimateTokens(text)
        assertTrue(tokens > 0)
        assertEquals(text.length / 4, tokens)
    }

    @Test
    fun `buildEnrichedContext includes user profile and directives in system instruction`() {
        val profile = UserProfile(
            id = "1",
            name = "John Doe",
            preferredName = "Johnny",
            personaPreference = "friendly",
        )

        val enriched = contextManager.buildEnrichedContext(
            allMessages = emptyList(),
            userProfile = profile,
            relevantMemories = emptyList(),
            activeTasks = emptyList(),
            buddyMode = BuddyMode.ACTIVE,
            userMessage = "Hello",
        )

        assertTrue(enriched.systemInstruction.contains("Johnny"))
        assertTrue(enriched.systemInstruction.contains("ACTIVE"))
        assertTrue(enriched.systemInstruction.contains("BUDDY_ACTION"))
    }

    @Test
    fun `buildContext respects token budget`() {
        // 100 messages, each with tokenCount = 100 → total 10,000 tokens (> 4096 budget)
        val messages = (1..100).map { i ->
            Message(
                id = "$i",
                conversationId = "c1",
                role = MessageRole.USER,
                content = "Message number $i " + "x".repeat(400),
                timestamp = System.currentTimeMillis() + i,
                tokenCount = 100,
            )
        }

        val windowed = contextManager.buildContext(messages, systemTokenCost = 500)
        assertTrue(windowed.size < messages.size)
        // Last message should be preserved (most recent)
        assertEquals(messages.last().id, windowed.last().id)
    }
}
