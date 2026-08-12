package com.buddy.aios.core.domain.agent

/**
 * Represents the execution plan generated for a specific [AgentGoal].
 */
data class AgentPlan(
    val goalId: String,
    val steps: List<AgentStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val status: AgentPlanStatus = AgentPlanStatus.PLANNING,
) {
    val currentStep: AgentStep?
        get() = steps.getOrNull(currentStepIndex)

    val isCompleted: Boolean
        get() = status == AgentPlanStatus.COMPLETED || currentStepIndex >= steps.size
}

enum class AgentPlanStatus {
    PLANNING,
    READY,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELLED,
}
