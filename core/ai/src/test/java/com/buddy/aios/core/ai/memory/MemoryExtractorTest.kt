package com.buddy.aios.core.ai.memory

import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.result.Result
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MemoryExtractorTest {

    private lateinit var memoryRepository: IMemoryRepository
    private lateinit var memoryExtractor: MemoryExtractor

    @BeforeEach
    fun setUp() {
        memoryRepository = mockk(relaxed = true)
        memoryExtractor = MemoryExtractor(memoryRepository)
    }

    @Test
    fun `isMemoryWorthy detects explicit preferences and facts`() {
        assertTrue(memoryExtractor.isMemoryWorthy("I prefer studying in the morning"))
        assertTrue(memoryExtractor.isMemoryWorthy("Remember that I work as a developer"))
        assertTrue(memoryExtractor.isMemoryWorthy("My goal is to learn Kotlin"))

        assertFalse(memoryExtractor.isMemoryWorthy("Hello how are you"))
        assertFalse(memoryExtractor.isMemoryWorthy("What is Docker?"))
    }

    @Test
    fun `maybeExtract skips when BuddyMode is OFF`() = runTest {
        memoryExtractor.maybeExtract(
            userMessage = "I prefer studying in the morning",
            buddyMode = BuddyMode.OFF,
            privacyLevel = PrivacyLevel.LOCAL_ONLY,
            conversationId = "c1",
        )

        coVerify(exactly = 0) { memoryRepository.saveMemory(any()) }
    }

    @Test
    fun `maybeExtract saves memory when content is memorable and mode is ACTIVE`() = runTest {
        coEvery { memoryRepository.saveMemory(any()) } returns Result.Success(Unit)

        memoryExtractor.maybeExtract(
            userMessage = "Remember that I prefer studying in the morning",
            buddyMode = BuddyMode.ACTIVE,
            privacyLevel = PrivacyLevel.LOCAL_ONLY,
            conversationId = "c1",
        )

        coVerify(exactly = 1) { memoryRepository.saveMemory(any()) }
    }
}
