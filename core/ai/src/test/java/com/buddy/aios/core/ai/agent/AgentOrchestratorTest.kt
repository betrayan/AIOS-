package com.buddy.aios.core.ai.agent

import com.buddy.aios.core.ai.engine.AIChunk
import com.buddy.aios.core.ai.engine.AIEngine
import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.domain.agent.ActionRisk
import com.buddy.aios.core.domain.agent.AgentStatus
import com.buddy.aios.core.domain.agent.GoalType
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.entity.MessageRole
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.repository.ITaskRepository
import com.buddy.aios.core.domain.result.Result
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentOrchestratorTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private val aiEngine: AIEngine = mockk(relaxed = true)
    private val goalAnalyzer = GoalAnalyzer()
    private val planner = Planner()
    private val permissionChecker = PermissionChecker()
    private val toolRouter: ToolRouter = mockk(relaxed = true)
    private val verifier: AgentVerifier = mockk(relaxed = true)
    private val buddyModeRepository: IBuddyModeRepository = mockk(relaxed = true)
    private val memoryRepository: IMemoryRepository = mockk(relaxed = true)
    private val taskRepository: ITaskRepository = mockk(relaxed = true)

    private lateinit var orchestrator: AgentOrchestrator

    @BeforeEach
    fun setUp() {
        coEvery { buddyModeRepository.getBuddyMode() } returns BuddyMode.ACTIVE
        coEvery { memoryRepository.searchMemories(any()) } returns Result.Success(emptyList())
        coEvery { taskRepository.getUpcomingTasks(any()) } returns Result.Success(emptyList())

        orchestrator = AgentOrchestrator(
            aiEngine = aiEngine,
            goalAnalyzer = goalAnalyzer,
            planner = planner,
            permissionChecker = permissionChecker,
            toolRouter = toolRouter,
            verifier = verifier,
            buddyModeRepository = buddyModeRepository,
            memoryRepository = memoryRepository,
            taskRepository = taskRepository,
            dispatchers = dispatchers,
        )
    }

    @Test
    fun `Test 1 - Simple question returns direct answer with zero multi-step planning`() = runTest {
        val request = "What is Java?"
        coEvery { aiEngine.complete(any()) } returns flowOf(
            Result.Success(AIChunk(text = "Java is a popular object-oriented programming language.", isComplete = true))
        )

        val result = orchestrator.executeGoal(request)

        assertTrue(result.success)
        assertTrue(result.summary.contains("Java is a popular object-oriented programming language"))
        assertTrue(result.completedSteps.isEmpty(), "Simple questions must have 0 multi-step planning steps")
    }

    @Test
    fun `Test 2 - Single task creation is executed and verified`() = runTest {
        val request = "Remind me to study Java at 7 PM"

        coEvery { toolRouter.routeAndExecute(any()) } returns ToolRouterResult.Success(
            tool = com.buddy.aios.core.ai.tool.BuddyTool.CreateTask(title = "Study Java"),
            toolResult = com.buddy.aios.core.ai.tool.ToolResult.Success("Task created")
        )
        coEvery { verifier.verify(any()) } returns VerificationResult(isVerified = true, details = "Task verified")

        val result = orchestrator.executeGoal(request)

        assertTrue(result.success)
        assertTrue(result.summary.contains("Study Java"))
        assertEquals(1, result.completedSteps.size)
    }

    @Test
    fun `Test 3 - Memory action is executed and verified`() = runTest {
        val request = "Remember that I want to become a DevOps engineer"
        coEvery { toolRouter.routeAndExecute(any()) } returns ToolRouterResult.Success(
            tool = com.buddy.aios.core.ai.tool.BuddyTool.SaveMemory(content = "I want to become a DevOps engineer"),
            toolResult = com.buddy.aios.core.ai.tool.ToolResult.Success("Memory saved")
        )
        coEvery { verifier.verify(any()) } returns VerificationResult(isVerified = true, details = "Memory verified")

        val result = orchestrator.executeGoal(request)

        assertTrue(result.success)
        assertTrue(result.summary.contains("I'll remember that"))
    }

    @Test
    fun `Test 4 - Multi-step goal generates and executes multi-step plan`() = runTest {
        val request = "Help me prepare for my Java interview"
        coEvery { toolRouter.routeAndExecute(any()) } returns ToolRouterResult.Success(
            tool = com.buddy.aios.core.ai.tool.BuddyTool.CreateTask(title = "Study Java OOP"),
            toolResult = com.buddy.aios.core.ai.tool.ToolResult.Success("Task created")
        )
        coEvery { verifier.verify(any()) } returns VerificationResult(isVerified = true, details = "Verified")
        coEvery { aiEngine.complete(any()) } returns flowOf(
            Result.Success(AIChunk(text = "Here are your mock interview questions and practice tips.", isComplete = true))
        )

        val result = orchestrator.executeGoal(request)

        assertTrue(result.success)
        assertTrue(result.completedSteps.size >= 2, "Multi-step goal must generate multiple steps")
    }

    @Test
    fun `Test 5 - Ambiguous request prompts for user clarification`() = runTest {
        val request = "Delete it"

        val result = orchestrator.executeGoal(request)

        assertFalse(result.success)
        assertTrue(result.requiresUserAction)
        assertTrue(result.summary.contains("specify what task or item"))
        assertEquals(AgentStatus.WAITING_CONFIRMATION, orchestrator.agentStatus.value)
    }

    @Test
    fun `Test 6 - High-risk bulk action requires explicit confirmation`() = runTest {
        val request = "Delete all my memories"

        val result = orchestrator.executeGoal(request)

        assertFalse(result.success)
        assertTrue(result.requiresUserAction)
        assertTrue(result.summary.contains("delete all stored memories") || result.summary.contains("memories"))
        assertEquals(AgentStatus.WAITING_CONFIRMATION, orchestrator.agentStatus.value)

        // Verify memory deletion was NOT performed prior to confirmation
        coVerify(exactly = 0) { memoryRepository.deleteMemory(any()) }

        // Confirm goal execution
        coEvery { memoryRepository.searchMemories("") } returns Result.Success(
            listOf(Memory(id = "m1", userId = "local", summary = "Mem 1", sourceConversationId = null, importance = 0.8f, createdAt = 100L, lastAccessedAt = 100L, expiresAt = null))
        )
        coEvery { memoryRepository.deleteMemory("m1") } returns Result.Success(Unit)

        val confirmResult = orchestrator.confirmGoal(confirmed = true)

        assertTrue(confirmResult.success)
        assertTrue(confirmResult.summary.contains("cleared"))
        coVerify(exactly = 1) { memoryRepository.deleteMemory("m1") }
    }

    @Test
    fun `Test 7 - Tool failure results in controlled failure response`() = runTest {
        val request = "Remind me to study Java"
        coEvery { toolRouter.routeAndExecute(any()) } returns ToolRouterResult.Failure("Database disk write error")

        val result = orchestrator.executeGoal(request)

        assertFalse(result.success)
        assertEquals(1, result.failedSteps.size)
    }

    @Test
    fun `Test 8 - Verification failure prevents false success report`() = runTest {
        val request = "Remind me to study Java"
        coEvery { toolRouter.routeAndExecute(any()) } returns ToolRouterResult.Success(
            tool = com.buddy.aios.core.ai.tool.BuddyTool.CreateTask(title = "Study Java"),
            toolResult = com.buddy.aios.core.ai.tool.ToolResult.Success("Task created")
        )
        // Empirical verification fails (DB query returns false)
        coEvery { verifier.verify(any()) } returns VerificationResult(isVerified = false, details = "DB query failed")

        val result = orchestrator.executeGoal(request)

        assertFalse(result.success, "Must NOT claim success when DB verification fails")
    }

    @Test
    fun `Test 9 - Safe retry attempts execution once on transient failure`() = runTest {
        val request = "Remind me to study Java"
        // First call fails, second succeeds
        coEvery { toolRouter.routeAndExecute(any()) } returnsMany listOf(
            ToolRouterResult.Failure("Transient timeout"),
            ToolRouterResult.Success(
                tool = com.buddy.aios.core.ai.tool.BuddyTool.CreateTask(title = "Study Java"),
                toolResult = com.buddy.aios.core.ai.tool.ToolResult.Success("Task created")
            )
        )
        coEvery { verifier.verify(any()) } returns VerificationResult(isVerified = true, details = "Verified")

        val result = orchestrator.executeGoal(request)

        assertTrue(result.success, "Safe retry should recover transient failures")
        coVerify(exactly = 2) { toolRouter.routeAndExecute(any()) }
    }

    @Test
    fun `Test 10 - Multi-step partial failure accurately reports completed vs failed steps`() = runTest {
        val request = "Help me prepare for my Java interview"

        // First tool step succeeds, second tool step fails
        coEvery { toolRouter.routeAndExecute(any()) } returnsMany listOf(
            ToolRouterResult.Success(
                tool = com.buddy.aios.core.ai.tool.BuddyTool.CreateTask(title = "Study Java"),
                toolResult = com.buddy.aios.core.ai.tool.ToolResult.Success("Task created")
            ),
            ToolRouterResult.Failure("Memory DB full")
        )
        coEvery { verifier.verify(any()) } returns VerificationResult(isVerified = true, details = "Verified")

        val result = orchestrator.executeGoal(request)

        assertFalse(result.success)
        assertTrue(result.completedSteps.isNotEmpty())
        assertTrue(result.failedSteps.isNotEmpty())
        assertTrue(result.summary.contains("completed") && result.summary.contains("couldn't complete"))
    }

    @Test
    fun `Test 11 - Goal cancellation halts remaining steps`() = runTest {
        orchestrator.cancelGoal()
        val result = orchestrator.executeGoal("Stop")

        assertTrue(result.success)
        assertEquals("User requested cancellation.", result.cancellationReason)
    }

    @Test
    fun `Test 12 - BuddyMode OFF blocks agent execution`() = runTest {
        coEvery { buddyModeRepository.getBuddyMode() } returns BuddyMode.OFF

        val result = orchestrator.executeGoal("What is Java?")

        assertFalse(result.success)
        assertTrue(result.summary.contains("turned OFF"))
    }

    @Test
    fun `Test 13 - BuddyMode SILENT allows text processing`() = runTest {
        coEvery { buddyModeRepository.getBuddyMode() } returns BuddyMode.SILENT
        coEvery { aiEngine.complete(any()) } returns flowOf(
            Result.Success(AIChunk(text = "Java explanation in text.", isComplete = true))
        )

        val result = orchestrator.executeGoal("What is Java?")

        assertTrue(result.success)
        assertTrue(result.summary.contains("Java explanation"))
    }

    @Test
    fun `Test 14 - LOCAL_ONLY privacy setting is respected`() = runTest {
        val request = "What is Docker?"
        coEvery { aiEngine.complete(any()) } returns flowOf(
            Result.Success(AIChunk(text = "Docker is a container platform.", isComplete = true))
        )

        val result = orchestrator.executeGoal(request)

        assertTrue(result.success)
    }

    @Test
    fun `Test 15 - Follow-up conversation maintains context`() = runTest {
        val followUp = "How does it compare to virtual machines?"
        val history = listOf(
            Message(id = "1", conversationId = "c1", role = MessageRole.USER, content = "What is Docker?", timestamp = 100L),
            Message(id = "2", conversationId = "c1", role = MessageRole.ASSISTANT, content = "Docker is a container platform.", timestamp = 200L)
        )

        coEvery { aiEngine.complete(any()) } returns flowOf(
            Result.Success(AIChunk(text = "Containers share the OS kernel while VMs virtualize hardware.", isComplete = true))
        )

        val result = orchestrator.executeGoal(followUp, conversationId = "c1", history = history)

        assertTrue(result.success)
        assertTrue(result.summary.contains("Containers share"))
    }

    @Test
    fun `Test 16 - Memory context adapts multi-step plan`() = runTest {
        val request = "Help me prepare for my Java interview"

        // User already has OOP memory saved
        coEvery { memoryRepository.searchMemories(any()) } returns Result.Success(
            listOf(Memory(id = "m1", userId = "local", summary = "Completed Java OOP basics", sourceConversationId = null, importance = 0.8f, createdAt = 100L, lastAccessedAt = 100L, expiresAt = null))
        )
        coEvery { toolRouter.routeAndExecute(any()) } returns ToolRouterResult.Success(
            tool = com.buddy.aios.core.ai.tool.BuddyTool.CreateTask(title = "Study Advanced Java Concurrency"),
            toolResult = com.buddy.aios.core.ai.tool.ToolResult.Success("Task created")
        )
        coEvery { verifier.verify(any()) } returns VerificationResult(isVerified = true, details = "Verified")
        coEvery { aiEngine.complete(any()) } returns flowOf(
            Result.Success(AIChunk(text = "Practice topics ready.", isComplete = true))
        )

        val result = orchestrator.executeGoal(request)

        assertTrue(result.success)
        assertTrue(result.completedSteps.any { it.description.contains("Advanced Java Concurrency") })
    }

    @Test
    fun `Test 17 - Active task context is considered`() = runTest {
        val request = "What should I work on today?"
        coEvery { taskRepository.getUpcomingTasks(any()) } returns Result.Success(
            listOf(Task(id = "t1", title = "Finish Docker assignment", isCompleted = false, createdAt = 100L, priority = TaskPriority.HIGH))
        )
        coEvery { aiEngine.complete(any()) } returns flowOf(
            Result.Success(AIChunk(text = "You should focus on your high-priority task: Finish Docker assignment.", isComplete = true))
        )

        val result = orchestrator.executeGoal(request)

        assertTrue(result.success)
    }

    @Test
    fun `Test 18 - Simple coding question returns normal AI code response`() = runTest {
        val request = "Write a Python script to sort a list"
        coEvery { aiEngine.complete(any()) } returns flowOf(
            Result.Success(AIChunk(text = "```python\nnumbers = [3, 1, 2]\nnumbers.sort()\n```", isComplete = true))
        )

        val result = orchestrator.executeGoal(request)

        assertTrue(result.success)
        assertTrue(result.summary.contains("numbers.sort()"))
    }

    @Test
    fun `Test 19 - Coding problem invokes diagnostic workflow`() = runTest {
        val request = "My Spring Boot app crashes on startup with NullPointerException"
        coEvery { aiEngine.complete(any()) } returns flowOf(
            Result.Success(AIChunk(text = "A NullPointerException on startup is usually caused by uninjected @Autowired beans. Check component scanning.", isComplete = true))
        )

        val result = orchestrator.executeGoal(request)

        assertTrue(result.success)
        assertTrue(result.summary.contains("NullPointerException"))
    }

    @Test
    fun `Test 20 - Unsupported capability returns honest limitation explanation`() = runTest {
        val request = "Build me a Python expense tracker"
        coEvery { aiEngine.complete(any()) } returns flowOf(
            Result.Success(AIChunk(text = "I can design the Python expense tracker code for you here, but I don't have direct access to your local filesystem to save files.", isComplete = true))
        )

        val result = orchestrator.executeGoal(request)

        assertTrue(result.success)
        assertTrue(result.summary.contains("don't have direct access"))
    }
}
