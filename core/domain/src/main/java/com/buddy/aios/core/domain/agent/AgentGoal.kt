package com.buddy.aios.core.domain.agent

import java.util.UUID

/**
 * Represents the overall goal a user is trying to accomplish.
 */
data class AgentGoal(
    val id: String = UUID.randomUUID().toString(),
    val originalRequest: String,
    val normalizedGoal: String,
    val status: AgentGoalStatus = AgentGoalStatus.PENDING,
    val goalType: GoalType = GoalType.GOAL,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Status lifecycle of an [AgentGoal].
 */
enum class AgentGoalStatus {
    PENDING,
    PLANNING,
    WAITING_FOR_USER,
    EXECUTING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

/**
 * Intent classification for user requests.
 */
enum class GoalType {
    QUESTION,
    COMMAND,
    GOAL,
    PROBLEM,
    CONVERSATION,
    AMBIGUOUS_REQUEST,
}
