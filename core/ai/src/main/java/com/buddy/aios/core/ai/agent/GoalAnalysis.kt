package com.buddy.aios.core.ai.agent

import com.buddy.aios.core.domain.agent.ActionRisk
import com.buddy.aios.core.domain.agent.GoalType

/**
 * Result of analyzing a user request via [GoalAnalyzer].
 */
sealed interface GoalAnalysis {

    /** Simple informational question — handled directly via AIOrchestrator (no multi-step planning). */
    data class SimpleQuestion(
        val originalRequest: String,
        val topic: String = "",
    ) : GoalAnalysis

    /** Single-step tool action (e.g. create task, save memory). */
    data class SingleStepAction(
        val originalRequest: String,
        val goalType: GoalType,
        val toolName: String,
        val arguments: Map<String, String>,
        val riskLevel: ActionRisk = ActionRisk.LOW,
    ) : GoalAnalysis

    /** Multi-step goal requiring structured planning. */
    data class MultiStepGoal(
        val originalRequest: String,
        val normalizedGoal: String,
        val priority: Int = 0,
    ) : GoalAnalysis

    /** Technical problem / debugging workflow requiring diagnostic steps. */
    data class DiagnosticProblem(
        val originalRequest: String,
        val problemArea: String,
        val missingInfoNeeded: String? = null,
    ) : GoalAnalysis

    /** Casual conversation request. */
    data class Conversational(
        val originalRequest: String,
    ) : GoalAnalysis

    /** Ambiguous request needing user clarification (e.g., "Delete it"). */
    data class AmbiguousRequest(
        val originalRequest: String,
        val clarificationQuestion: String,
    ) : GoalAnalysis

    /** High-risk operation requiring explicit confirmation gate. */
    data class HighRiskConfirmation(
        val originalRequest: String,
        val goalType: GoalType,
        val actionDescription: String,
        val toolName: String,
        val arguments: Map<String, String> = emptyMap(),
        val riskLevel: ActionRisk = ActionRisk.HIGH,
    ) : GoalAnalysis
}
