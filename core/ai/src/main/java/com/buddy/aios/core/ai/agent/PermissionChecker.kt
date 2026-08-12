package com.buddy.aios.core.ai.agent

import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.agent.ActionRisk
import com.buddy.aios.core.domain.agent.AgentGoal
import com.buddy.aios.core.domain.agent.AgentStep
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PermissionResult {
    data object Granted : PermissionResult
    data class RequiresConfirmation(
        val confirmationMessage: String,
        val riskLevel: ActionRisk,
    ) : PermissionResult
}

/**
 * Enforces action permissions based on [ActionRisk] classification.
 *
 * Rules:
 * - LOW: Harmless actions (answering questions, creating normal reminders, saving memory) -> Granted
 * - MEDIUM: Modifying tasks, single memory deletion -> Granted
 * - HIGH: Bulk task/memory deletion, changing privacy/cloud settings -> Requires Confirmation
 */
@Singleton
class PermissionChecker @Inject constructor() {

    companion object {
        private const val TAG = "PermissionChecker"
    }

    fun checkPermission(
        goal: AgentGoal,
        step: AgentStep,
        isUserConfirmed: Boolean = false,
    ): PermissionResult {
        AppLogger.d(TAG, "Checking permission for step '${step.description}' (risk=${step.riskLevel}, confirmed=$isUserConfirmed)")

        if (isUserConfirmed) {
            return PermissionResult.Granted
        }

        if (step.requiresConfirmation || step.riskLevel == ActionRisk.HIGH) {
            val message = buildConfirmationPrompt(goal, step)
            return PermissionResult.RequiresConfirmation(
                confirmationMessage = message,
                riskLevel = step.riskLevel,
            )
        }

        return PermissionResult.Granted
    }

    private fun buildConfirmationPrompt(goal: AgentGoal, step: AgentStep): String {
        return when {
            step.description.contains("delete all memories", ignoreCase = true) ||
            step.arguments["bulk"] == "true" -> {
                "I can do that, but this will permanently remove all stored memories. Do you want me to continue?"
            }
            step.description.contains("delete all tasks", ignoreCase = true) -> {
                "This will permanently delete all your tasks. Should I continue?"
            }
            else -> {
                "This action ('${step.description}') has a high impact. Please confirm if you want me to proceed."
            }
        }
    }
}
