package com.buddy.aios.core.ai.engine

import app.cash.turbine.test
import com.buddy.aios.core.ai.context.ContextManager
import com.buddy.aios.core.ai.memory.MemoryExtractor
import com.buddy.aios.core.ai.policy.AIPolicy
import com.buddy.aios.core.ai.policy.DefaultAIPolicy
import com.buddy.aios.core.ai.provider.AIProvider
import com.buddy.aios.core.ai.tool.IntentParser
import com.buddy.aios.core.ai.tool.ToolExecutor
import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.entity.UserProfile
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.repository.ITaskRepository
import com.buddy.aios.core.domain.repository.IUserRepository
import com.buddy.aios.core.domain.result.Result
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AIOrchestratorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
        override val unconfined = testDispatcher
    }

    private val policy: AIPolicy = DefaultAIPolicy()
    private val contextManager = ContextManager()
    private val memoryExtractor: MemoryExtractor = mockk(relaxed = true)
    private val toolExecutor: ToolExecutor = mockk(relaxed = true)

    private val localProvider: AIProvider = mockk()
    private val cloudProvider: AIProvider = mockk()

    private val buddyModeRepository: IBuddyModeRepository = mockk()
    private val memoryRepository: IMemoryRepository = mockk(relaxed = true)
    private val taskRepository: ITaskRepository = mockk(relaxed = true)
    private val userRepository: IUserRepository = mockk(relaxed = true)

    private lateinit var orchestrator: AIOrchestrator

    @BeforeEach
    fun setUp() {
        every { localProvider.name } returns "LocalAI/Gemma"
        every { cloudProvider.name } returns "CloudAI/Gemini"
        coEvery { localProvider.isAvailable() } returns false
        coEvery { cloudProvider.isAvailable() } returns false

        coEvery { buddyModeRepository.getBuddyMode() } returns BuddyMode.ACTIVE
        coEvery { userRepository.getUserProfile() } returns Result.Success(
            UserProfile("1", "User", "User", "default", privacyLevel = PrivacyLevel.LOCAL_ONLY)
        )
        coEvery { memoryRepository.searchMemories(any()) } returns Result.Success(emptyList())
        coEvery { taskRepository.getUpcomingTasks() } returns Result.Success(emptyList())

        orchestrator = AIOrchestrator(
            policy = policy,
            contextManager = contextManager,
            memoryExtractor = memoryExtractor,
            toolExecutor = toolExecutor,
            localProvider = localProvider,
            cloudProvider = cloudProvider,
            buddyModeRepository = buddyModeRepository,
            memoryRepository = memoryRepository,
            taskRepository = taskRepository,
            userRepository = userRepository,
            dispatchers = dispatchers,
        )
    }

    @Test
    fun `complete blocks execution when BuddyMode is OFF`() = runTest(testDispatcher) {
        coEvery { buddyModeRepository.getBuddyMode() } returns BuddyMode.OFF

        val prompt = AIPrompt("sys", emptyList(), "hello")
        orchestrator.complete(prompt).test {
            val item = awaitItem()
            assertTrue(item is Result.Error)
            awaitComplete()
        }
    }

    @Test
    fun `complete uses local provider by default under LOCAL_ONLY`() = runTest(testDispatcher) {
        coEvery { localProvider.isAvailable() } returns true
        every { localProvider.generate(any()) } returns flowOf(
            Result.Success(AIChunk(text = "Hello there!", isComplete = true))
        )

        val prompt = AIPrompt("sys", emptyList(), "hello")
        orchestrator.complete(prompt).test {
            val item = awaitItem()
            assertTrue(item is Result.Success)
            assertEquals("Hello there!", (item as Result.Success).value.text)
            awaitComplete()
        }
    }

    @Test
    fun `complete returns clear error message when local provider is unavailable under LOCAL_ONLY`() = runTest(testDispatcher) {
        coEvery { localProvider.isAvailable() } returns false

        val prompt = AIPrompt("sys", emptyList(), "hello")
        orchestrator.complete(prompt).test {
            val item = awaitItem()
            assertTrue(item is Result.Success)
            assertTrue((item as Result.Success).value.text.contains("local-only mode"))
            awaitComplete()
        }
    }
}
