package com.buddy.aios.core.ai.context

import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.entity.MessageRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConversationContextManagerTest {

    private lateinit var manager: ConversationContextManager

    @BeforeEach
    fun setUp() {
        manager = ConversationContextManager()
    }

    @Test
    fun `TEST 1 - Topic inference and pronoun resolution`() {
        val convId = "c1"

        // Turn 1: Discuss Docker
        manager.updateContextAfterTurn(
            conversationId = convId,
            userMessage = "What is Docker?",
            assistantResponse = "Docker packages applications into containers.",
        )

        val context = manager.getOrCreateContext(convId)
        assertEquals("Docker", context.currentTopic)

        // Turn 2: Follow-up question with pronoun "it"
        val resolved = manager.resolveContextualQuery(convId, "Why do people use it?")

        assertTrue(resolved.contains("Context topic: Docker"))
    }

    @Test
    fun `TEST 2 - Rolling summary created when conversation grows long`() {
        val convId = "c2"
        val messages = (1..12).map { i ->
            Message(
                id = "m$i",
                conversationId = convId,
                role = if (i % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
                content = "Message content $i",
                timestamp = System.currentTimeMillis(),
            )
        }

        manager.updateContextAfterTurn(
            conversationId = convId,
            userMessage = "How does Kubernetes help?",
            assistantResponse = "Kubernetes orchestrates Docker containers.",
            allMessages = messages,
        )

        val context = manager.getOrCreateContext(convId)
        assertTrue(context.rollingSummary != null, "Rolling summary should be created for 10+ messages")
        assertTrue(context.rollingSummary!!.contains("discussing"))
    }
}
