package com.buddy.aios.core.domain.agent

import java.util.UUID

/**
 * Represents a single executable action or step within an [AgentPlan].
 */
data class AgentStep(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val type: AgentStepType = AgentStepType.TOOL_ACTION,
    val status: AgentStepStatus = AgentStepStatus.PENDING,
    val toolName: String? = null,
    val arguments: Map<String, String> = emptyMap(),
    val result: Any? = null,
    val requiresConfirmation: Boolean = false,
    val riskLevel: ActionRisk = ActionRisk.LOW,
)

enum class AgentStepType {
    INFORMATIONAL,
    TOOL_ACTION,
    CLARIFICATION,
    CONFIRMATION,
}

enum class AgentStepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED,
    WAITING_CONFIRMATION,
}

/**
 * Action risk classification for security and user confirmation enforcement.
 */
enum class ActionRisk {
    /** Harmless operations: answering questions, normal task creation, saving requested memory. */
    LOW,

    /** Moderate operations: modifying existing tasks, changing basic settings, deleting a single memory. */
    MEDIUM,

    /** High impact operations: deleting multiple tasks/memories, changing privacy/cloud settings, destructive ops. */
    HIGH,
}
