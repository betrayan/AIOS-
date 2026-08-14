package com.buddy.aios.core.ai.brain

import com.buddy.aios.core.domain.entity.Task

enum class ActionType {
    ANSWER,
    EXECUTE_TOOL,
    ASK_CLARIFICATION,
    SUGGEST,
    NOTIFY,
    DO_NOTHING,
}

enum class DecisionConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

/**
 * Encapsulates the output decision of the Personal Intelligence Brain.
 */
data class ActionDecision(
    val actionType: ActionType,
    val confidence: DecisionConfidence,
    val primaryTextResponse: String,
    val voiceTextResponse: String? = null,
    val toolName: String? = null,
    val toolArguments: Map<String, String> = emptyMap(),
    val targetTask: Task? = null,
    val clarificationQuestion: String? = null,
    val userExplanation: String? = null,
    val priority: PriorityLevel = PriorityLevel.MEDIUM,
)
