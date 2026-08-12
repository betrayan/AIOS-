package com.buddy.aios.core.ai.agent

import com.buddy.aios.core.ai.engine.AIEngine
import com.buddy.aios.core.ai.engine.AIChunk
import com.buddy.aios.core.ai.engine.AIPrompt
import com.buddy.aios.core.ai.tool.BuddyTool
import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.agent.AgentGoal
import com.buddy.aios.core.domain.agent.AgentGoalStatus
import com.buddy.aios.core.domain.agent.AgentPlan
import com.buddy.aios.core.domain.agent.AgentPlanStatus
import com.buddy.aios.core.domain.agent.AgentResult
import com.buddy.aios.core.domain.agent.AgentStatus
import com.buddy.aios.core.domain.agent.AgentStep
import com.buddy.aios.core.domain.agent.AgentStepStatus
import com.buddy.aios.core.domain.agent.AgentStepType
import com.buddy.aios.core.domain.agent.GoalType
import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.repository.ITaskRepository
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AIOS Agent Brain Master Orchestrator.
 *
 * Coordinates goal understanding, planning, risk enforcement, tool execution,
 * empirical verification, and response synthesis.
 *
 * Rules:
 * - Does NOT create a second AI Engine. Uses [AIEngine] (AIOrchestrator).
 * - Does NOT use planner for simple questions.
 * - Does NOT claim success without verification by [AgentVerifier].
 * - Supports goal cancellation via [cancelGoal].
 * - Supports confirmation gates for high-risk operations via [confirmGoal].
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    private val aiEngine: AIEngine,
    private val goalAnalyzer: GoalAnalyzer,
    private val planner: Planner,
    private val permissionChecker: PermissionChecker,
    private val toolRouter: ToolRouter,
    private val verifier: AgentVerifier,
    private val buddyModeRepository: IBuddyModeRepository,
    private val memoryRepository: IMemoryRepository,
    private val taskRepository: ITaskRepository,
    private val dispatchers: DispatcherProvider,
) {
    companion object {
        private const val TAG = "AgentOrchestrator"
    }

    private val _agentStatus = MutableStateFlow(AgentStatus.IDLE)
    val agentStatus: StateFlow<AgentStatus> = _agentStatus.asStateFlow()

    private var activeGoal: AgentGoal? = null
    private var activePlan: AgentPlan? = null
    private var isCancelled: Boolean = false
    private var pendingHighRiskAnalysis: GoalAnalysis.HighRiskConfirmation? = null

    /**
     * Executes a user request through the Agent Brain workflow.
     *
     * @param request        The raw user input.
     * @param conversationId Active conversation ID.
     * @param history        Token-windowed conversation history.
     */
    suspend fun executeGoal(
        request: String,
        conversationId: String = "",
        history: List<Message> = emptyList(),
    ): AgentResult = withContext(dispatchers.default) {
        val trimmed = request.trim()
        isCancelled = false
        _agentStatus.value = AgentStatus.UNDERSTANDING

        AppLogger.d(TAG, "Agent executing goal: '$trimmed'")

        // Check BuddyMode — block agent if OFF
        val buddyMode = buddyModeRepository.getBuddyMode()
        if (buddyMode == com.buddy.aios.core.domain.entity.BuddyMode.OFF) {
            _agentStatus.value = AgentStatus.FAILED
            return@withContext AgentResult(
                success = false,
                summary = "Buddy is currently turned OFF.",
                details = "BuddyMode.OFF prevents agent processing according to AIPolicy.",
            )
        }

        // Handle active goal cancellation request ("Stop" / "Cancel")
        if (trimmed.equals("stop", ignoreCase = true) || trimmed.equals("cancel", ignoreCase = true)) {
            cancelGoal()
            _agentStatus.value = AgentStatus.IDLE
            return@withContext AgentResult(
                success = true,
                summary = "Okay, I've stopped the current task.",
                cancellationReason = "User requested cancellation.",
            )
        }

        // Fetch contextual memories and tasks for goal analysis
        val relevantMemories = (memoryRepository.searchMemories(trimmed.take(60)) as? Result.Success)?.value ?: emptyList()
        val activeTasks = (taskRepository.getUpcomingTasks(0L) as? Result.Success)?.value ?: emptyList()

        // 1. Goal Understanding
        val analysis = goalAnalyzer.analyze(trimmed, relevantMemories, activeTasks)
        val goal = AgentGoal(
            originalRequest = trimmed,
            normalizedGoal = getNormalizedGoalText(analysis, trimmed),
            status = AgentGoalStatus.PLANNING,
            goalType = getGoalType(analysis),
        )
        activeGoal = goal

        // 2. Handle Simple Informational Questions & Conversational Requests (0 planning overhead)
        if (analysis is GoalAnalysis.SimpleQuestion || analysis is GoalAnalysis.Conversational) {
            _agentStatus.value = AgentStatus.COMPLETED
            val aiResponse = queryAiEngineDirect(trimmed, history, conversationId)
            _agentStatus.value = AgentStatus.IDLE
            return@withContext AgentResult(
                success = true,
                summary = aiResponse,
            )
        }

        // 3. Handle Ambiguous Requests
        if (analysis is GoalAnalysis.AmbiguousRequest) {
            _agentStatus.value = AgentStatus.WAITING_CONFIRMATION
            return@withContext AgentResult(
                success = false,
                summary = analysis.clarificationQuestion,
                requiresUserAction = true,
            )
        }

        // 4. Handle High-Risk Operations (Confirmation Gate)
        if (analysis is GoalAnalysis.HighRiskConfirmation) {
            pendingHighRiskAnalysis = analysis
            _agentStatus.value = AgentStatus.WAITING_CONFIRMATION
            return@withContext AgentResult(
                success = false,
                summary = analysis.actionDescription,
                details = "High-risk operation requires explicit confirmation.",
                requiresUserAction = true,
            )
        }

        // 5. Build Plan for Single-Step, Multi-Step, or Diagnostic Goals
        _agentStatus.value = AgentStatus.PLANNING
        val plan = planner.buildPlan(goal, analysis, relevantMemories, activeTasks)
        activePlan = plan

        // 6. Execute Plan Steps
        val completedSteps = mutableListOf<AgentStep>()
        val failedSteps = mutableListOf<AgentStep>()

        for (stepIndex in plan.steps.indices) {
            if (isCancelled) {
                AppLogger.d(TAG, "Goal execution cancelled at step $stepIndex")
                _agentStatus.value = AgentStatus.FAILED
                return@withContext AgentResult(
                    success = false,
                    summary = "Task was cancelled.",
                    completedSteps = completedSteps,
                    failedSteps = failedSteps,
                    cancellationReason = "User cancelled execution.",
                )
            }

            val step = plan.steps[stepIndex]
            _agentStatus.value = AgentStatus.EXECUTING

            // Permission Check
            val permission = permissionChecker.checkPermission(goal, step)
            if (permission is PermissionResult.RequiresConfirmation) {
                _agentStatus.value = AgentStatus.WAITING_CONFIRMATION
                return@withContext AgentResult(
                    success = false,
                    summary = permission.confirmationMessage,
                    requiresUserAction = true,
                )
            }

            if (step.type == AgentStepType.TOOL_ACTION) {
                val stepResult = executeAndVerifyStep(step)
                if (stepResult.first) {
                    completedSteps.add(step.copy(status = AgentStepStatus.SUCCESS, result = stepResult.second))
                } else {
                    // Retry once if safe
                    AppLogger.w(TAG, "Step failed — attempting safe retry once: ${step.description}")
                    val retryResult = executeAndVerifyStep(step)
                    if (retryResult.first) {
                        completedSteps.add(step.copy(status = AgentStepStatus.SUCCESS, result = retryResult.second))
                    } else {
                        failedSteps.add(step.copy(status = AgentStepStatus.FAILED, result = retryResult.second))
                    }
                }
            } else {
                // Informational or reasoning step
                completedSteps.add(step.copy(status = AgentStepStatus.SUCCESS))
            }
        }

        // 7. Synthesize Final Verified Result
        val overallSuccess = failedSteps.isEmpty() && completedSteps.isNotEmpty()
        _agentStatus.value = if (overallSuccess) AgentStatus.COMPLETED else AgentStatus.FAILED

        val summaryText = buildFinalSummary(analysis, completedSteps, failedSteps, trimmed, history, conversationId)

        _agentStatus.value = AgentStatus.IDLE
        return@withContext AgentResult(
            success = overallSuccess,
            summary = summaryText,
            completedSteps = completedSteps,
            failedSteps = failedSteps,
        )
    }

    /**
     * Resumes execution after user confirms a high-risk operation or clarification.
     */
    suspend fun confirmGoal(confirmed: Boolean): AgentResult = withContext(dispatchers.default) {
        val analysis = pendingHighRiskAnalysis
        pendingHighRiskAnalysis = null

        if (!confirmed || analysis == null) {
            _agentStatus.value = AgentStatus.IDLE
            return@withContext AgentResult(
                success = false,
                summary = "Cancelled. I won't modify those items.",
            )
        }

        // User confirmed high-risk action: execute bulk operation
        _agentStatus.value = AgentStatus.EXECUTING
        return@withContext if (analysis.toolName == "DeleteMemory") {
            val memories = (memoryRepository.searchMemories("") as? Result.Success)?.value ?: emptyList()
            var allSucceeded = true
            memories.forEach { memory ->
                val delResult = memoryRepository.deleteMemory(memory.id)
                if (delResult is Result.Error) allSucceeded = false
            }
            _agentStatus.value = AgentStatus.COMPLETED
            if (allSucceeded) {
                AgentResult(success = true, summary = "All memories have been permanently cleared.")
            } else {
                AgentResult(success = false, summary = "Could not clear all memories.")
            }
        } else {
            val tasks = (taskRepository.getUpcomingTasks(0L) as? Result.Success)?.value ?: emptyList()
            var allSucceeded = true
            tasks.forEach { task ->
                val delResult = taskRepository.deleteTask(task.id)
                if (delResult is Result.Error) allSucceeded = false
            }
            _agentStatus.value = AgentStatus.COMPLETED
            if (allSucceeded) {
                AgentResult(success = true, summary = "All tasks have been permanently deleted.")
            } else {
                AgentResult(success = false, summary = "Could not delete all tasks.")
            }
        }
    }

    /**
     * Cancels active goal execution.
     */
    fun cancelGoal() {
        isCancelled = true
        activeGoal = activeGoal?.copy(status = AgentGoalStatus.CANCELLED)
        activePlan = activePlan?.copy(status = AgentPlanStatus.CANCELLED)
        pendingHighRiskAnalysis = null
        _agentStatus.value = AgentStatus.IDLE
        AppLogger.d(TAG, "Active goal cancelled")
    }

    // ── Helper Methods ────────────────────────────────────────────────────────

    private suspend fun executeAndVerifyStep(step: AgentStep): Pair<Boolean, String> {
        return when (val routeResult = toolRouter.routeAndExecute(step)) {
            is ToolRouterResult.Success -> {
                _agentStatus.value = AgentStatus.VERIFYING
                val verification = verifier.verify(routeResult.tool)
                if (verification.isVerified) {
                    true to verification.details
                } else {
                    false to "Verification failed: ${verification.details}"
                }
            }
            is ToolRouterResult.Failure -> false to routeResult.reason
            is ToolRouterResult.ValidationError -> false to routeResult.message
        }
    }

    private suspend fun queryAiEngineDirect(request: String, history: List<Message>, conversationId: String): String {
        var text = ""
        val prompt = AIPrompt(
            systemInstruction = "You are Buddy, a warm, intelligent personal AI companion sitting beside the user. Answer helpfully.",
            conversationHistory = history,
            userMessage = request,
            conversationId = conversationId,
        )
        aiEngine.complete(prompt).collect { chunkResult ->
            if (chunkResult is Result.Success) {
                text += chunkResult.value.text
            }
        }
        return text.ifBlank { "I've processed your request." }
    }

    private suspend fun buildFinalSummary(
        analysis: GoalAnalysis,
        completed: List<AgentStep>,
        failed: List<AgentStep>,
        originalRequest: String,
        history: List<Message>,
        conversationId: String,
    ): String {
        return when {
            failed.isNotEmpty() && completed.isNotEmpty() -> {
                // Multi-step partial failure: report exact status accurately!
                val completedDesc = completed.joinToString(", ") { it.description }
                val failedDesc = failed.joinToString(", ") { it.description }
                "I completed: $completedDesc. However, I couldn't complete: $failedDesc."
            }
            failed.isNotEmpty() -> {
                "I couldn't complete your request. ${failed.firstOrNull()?.result ?: ""}"
            }
            analysis is GoalAnalysis.SingleStepAction -> {
                when (analysis.toolName) {
                    "CreateTask" -> "Done. I've set your reminder for ${analysis.arguments["title"]}."
                    "SaveMemory" -> "Got it. I'll remember that."
                    "CompleteTask" -> "Marked completed."
                    "DeleteTask" -> "Removed task."
                    else -> "Done. I've updated that for you."
                }
            }
            analysis is GoalAnalysis.DiagnosticProblem -> {
                val diagnosticPrompt = "Explain solution simply for diagnostic problem: $originalRequest"
                queryAiEngineDirect(diagnosticPrompt, history, conversationId)
            }
            else -> {
                queryAiEngineDirect(originalRequest, history, conversationId)
            }
        }
    }

    private fun getNormalizedGoalText(analysis: GoalAnalysis, raw: String): String = when (analysis) {
        is GoalAnalysis.MultiStepGoal -> analysis.normalizedGoal
        is GoalAnalysis.DiagnosticProblem -> "Diagnose ${analysis.problemArea}"
        else -> raw
    }

    private fun getGoalType(analysis: GoalAnalysis): GoalType = when (analysis) {
        is GoalAnalysis.SimpleQuestion -> GoalType.QUESTION
        is GoalAnalysis.SingleStepAction -> GoalType.COMMAND
        is GoalAnalysis.MultiStepGoal -> GoalType.GOAL
        is GoalAnalysis.DiagnosticProblem -> GoalType.PROBLEM
        is GoalAnalysis.Conversational -> GoalType.CONVERSATION
        is GoalAnalysis.AmbiguousRequest -> GoalType.AMBIGUOUS_REQUEST
        is GoalAnalysis.HighRiskConfirmation -> GoalType.COMMAND
    }
}
