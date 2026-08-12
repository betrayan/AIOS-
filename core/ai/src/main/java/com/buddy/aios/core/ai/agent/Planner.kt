package com.buddy.aios.core.ai.agent

import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.agent.ActionRisk
import com.buddy.aios.core.domain.agent.AgentGoal
import com.buddy.aios.core.domain.agent.AgentPlan
import com.buddy.aios.core.domain.agent.AgentPlanStatus
import com.buddy.aios.core.domain.agent.AgentStep
import com.buddy.aios.core.domain.agent.AgentStepStatus
import com.buddy.aios.core.domain.agent.AgentStepType
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.entity.Task
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates structured execution plans ([AgentPlan]) from an [AgentGoal] and [GoalAnalysis].
 */
@Singleton
class Planner @Inject constructor() {

    companion object {
        private const val TAG = "Planner"
    }

    fun buildPlan(
        goal: AgentGoal,
        analysis: GoalAnalysis,
        relevantMemories: List<Memory> = emptyList(),
        activeTasks: List<Task> = emptyList(),
    ): AgentPlan {
        AppLogger.d(TAG, "Building plan for goal '${goal.normalizedGoal}' (${analysis.javaClass.simpleName})")

        return when (analysis) {
            is GoalAnalysis.SimpleQuestion, is GoalAnalysis.Conversational -> {
                // No plan needed — direct single pass via AIOrchestrator
                AgentPlan(
                    goalId = goal.id,
                    steps = emptyList(),
                    status = AgentPlanStatus.READY,
                )
            }

            is GoalAnalysis.SingleStepAction -> {
                val step = AgentStep(
                    description = goal.originalRequest,
                    type = AgentStepType.TOOL_ACTION,
                    status = AgentStepStatus.PENDING,
                    toolName = analysis.toolName,
                    arguments = analysis.arguments,
                    requiresConfirmation = false,
                    riskLevel = analysis.riskLevel,
                )
                AgentPlan(
                    goalId = goal.id,
                    steps = listOf(step),
                    status = AgentPlanStatus.READY,
                )
            }

            is GoalAnalysis.HighRiskConfirmation -> {
                val step = AgentStep(
                    description = analysis.actionDescription,
                    type = AgentStepType.CONFIRMATION,
                    status = AgentStepStatus.WAITING_CONFIRMATION,
                    toolName = analysis.toolName,
                    arguments = analysis.arguments,
                    requiresConfirmation = true,
                    riskLevel = ActionRisk.HIGH,
                )
                AgentPlan(
                    goalId = goal.id,
                    steps = listOf(step),
                    status = AgentPlanStatus.PLANNING,
                )
            }

            is GoalAnalysis.AmbiguousRequest -> {
                val step = AgentStep(
                    description = analysis.clarificationQuestion,
                    type = AgentStepType.CLARIFICATION,
                    status = AgentStepStatus.WAITING_CONFIRMATION,
                    requiresConfirmation = true,
                    riskLevel = ActionRisk.LOW,
                )
                AgentPlan(
                    goalId = goal.id,
                    steps = listOf(step),
                    status = AgentPlanStatus.PLANNING,
                )
            }

            is GoalAnalysis.MultiStepGoal -> {
                val steps = buildMultiStepPlan(goal, analysis, relevantMemories)
                AgentPlan(
                    goalId = goal.id,
                    steps = steps,
                    status = AgentPlanStatus.READY,
                )
            }

            is GoalAnalysis.DiagnosticProblem -> {
                val steps = listOf(
                    AgentStep(
                        description = "Gather diagnostic logs and analyze symptoms for ${analysis.problemArea}",
                        type = AgentStepType.INFORMATIONAL,
                        status = AgentStepStatus.PENDING,
                    ),
                    AgentStep(
                        description = "Formulate resolution steps and provide solution for ${analysis.problemArea}",
                        type = AgentStepType.INFORMATIONAL,
                        status = AgentStepStatus.PENDING,
                    ),
                )
                AgentPlan(
                    goalId = goal.id,
                    steps = steps,
                    status = AgentPlanStatus.READY,
                )
            }
        }
    }

    private fun buildMultiStepPlan(
        goal: AgentGoal,
        analysis: GoalAnalysis.MultiStepGoal,
        memories: List<Memory>,
    ): List<AgentStep> {
        val steps = mutableListOf<AgentStep>()

        // Check contextual memory adaptation rule: if memory shows user already completed part of the goal, skip!
        val memoryText = memories.joinToString(" ") { it.summary.lowercase() }
        val holdsOopKnowledge = memoryText.contains("oop") || memoryText.contains("java basic")

        if (goal.normalizedGoal.contains("interview", ignoreCase = true) ||
            goal.originalRequest.contains("interview", ignoreCase = true)) {

            steps.add(
                AgentStep(
                    description = "Identify key interview topics (Concurrency, JVM Memory, Spring Boot)",
                    type = AgentStepType.INFORMATIONAL,
                    status = AgentStepStatus.PENDING,
                )
            )

            // Dynamic plan adaptation: skip OOP if user already has OOP memory recorded
            val studyTopic = if (holdsOopKnowledge) {
                "Study Advanced Java Concurrency & Spring Boot microservices"
            } else {
                "Study Java OOP principles, Collections, and Concurrency"
            }

            steps.add(
                AgentStep(
                    description = "Create study reminder task: '$studyTopic'",
                    type = AgentStepType.TOOL_ACTION,
                    status = AgentStepStatus.PENDING,
                    toolName = "CreateTask",
                    arguments = mapOf("title" to studyTopic),
                )
            )

            steps.add(
                AgentStep(
                    description = "Record goal in memory: Preparing for Java interview",
                    type = AgentStepType.TOOL_ACTION,
                    status = AgentStepStatus.PENDING,
                    toolName = "SaveMemory",
                    arguments = mapOf("content" to "Preparing for Java technical interview"),
                )
            )

            steps.add(
                AgentStep(
                    description = "Provide mock interview practice questions and tips",
                    type = AgentStepType.INFORMATIONAL,
                    status = AgentStepStatus.PENDING,
                )
            )
        } else {
            // General multi-step goal plan
            steps.add(
                AgentStep(
                    description = "Define milestones for '${goal.normalizedGoal}'",
                    type = AgentStepType.INFORMATIONAL,
                    status = AgentStepStatus.PENDING,
                )
            )
            steps.add(
                AgentStep(
                    description = "Create action reminder: ${goal.normalizedGoal}",
                    type = AgentStepType.TOOL_ACTION,
                    status = AgentStepStatus.PENDING,
                    toolName = "CreateTask",
                    arguments = mapOf("title" to goal.normalizedGoal),
                )
            )
            steps.add(
                AgentStep(
                    description = "Summarize plan and recommended next steps",
                    type = AgentStepType.INFORMATIONAL,
                    status = AgentStepStatus.PENDING,
                )
            )
        }

        return steps
    }
}
