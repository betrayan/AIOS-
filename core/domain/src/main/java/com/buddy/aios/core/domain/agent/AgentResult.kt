package com.buddy.aios.core.domain.agent

/**
 * Contains the final verified result of an [AgentGoal] execution.
 */
data class AgentResult(
    val success: Boolean,
    val summary: String,
    val details: String = "",
    val completedSteps: List<AgentStep> = emptyList(),
    val failedSteps: List<AgentStep> = emptyList(),
    val requiresUserAction: Boolean = false,
    val cancellationReason: String? = null,
)
