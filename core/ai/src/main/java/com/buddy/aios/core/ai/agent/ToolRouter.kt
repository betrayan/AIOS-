package com.buddy.aios.core.ai.agent

import com.buddy.aios.core.ai.tool.BuddyTool
import com.buddy.aios.core.ai.tool.ToolExecutor
import com.buddy.aios.core.ai.tool.ToolResult
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.agent.AgentStep
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ToolRouterResult {
    data class Success(val tool: BuddyTool, val toolResult: ToolResult.Success) : ToolRouterResult
    data class Failure(val reason: String) : ToolRouterResult
    data class ValidationError(val message: String) : ToolRouterResult
}

/**
 * Maps [AgentStep] requests to existing [BuddyTool] instances and executes them via [ToolExecutor].
 *
 * Rules:
 * - Does NOT duplicate tool logic. Uses [ToolExecutor].
 * - Validates required arguments before calling [ToolExecutor].
 */
@Singleton
class ToolRouter @Inject constructor(
    private val toolExecutor: ToolExecutor,
) {
    companion object {
        private const val TAG = "ToolRouter"
    }

    suspend fun routeAndExecute(step: AgentStep): ToolRouterResult {
        val toolName = step.toolName
        if (toolName.isNullOrBlank()) {
            return ToolRouterResult.Failure("No tool specified for step: '${step.description}'")
        }

        AppLogger.d(TAG, "Routing step to tool '$toolName' with args: ${step.arguments}")

        val tool = try {
            mapToTool(toolName, step.arguments)
        } catch (e: IllegalArgumentException) {
            return ToolRouterResult.ValidationError(e.message ?: "Invalid arguments for tool '$toolName'")
        }

        return when (val result = toolExecutor.execute(tool)) {
            is ToolResult.Success -> ToolRouterResult.Success(tool, result)
            is ToolResult.Failure -> ToolRouterResult.Failure(result.reason)
            is ToolResult.ValidationError -> ToolRouterResult.ValidationError("Tool arguments failed validation check")
        }
    }

    private fun mapToTool(toolName: String, args: Map<String, String>): BuddyTool {
        return when (toolName) {
            "CreateTask" -> {
                val title = args["title"]?.trim() ?: throw IllegalArgumentException("Task title is required")
                if (title.isBlank()) throw IllegalArgumentException("Task title cannot be blank")
                val dueTimestamp = args["dueTimestamp"]?.toLongOrNull()
                BuddyTool.CreateTask(title = title, dueTimestamp = dueTimestamp)
            }

            "CompleteTask" -> {
                val title = args["title"]?.trim() ?: throw IllegalArgumentException("Task title is required")
                if (title.isBlank()) throw IllegalArgumentException("Task title cannot be blank")
                BuddyTool.CompleteTask(title = title)
            }

            "DeleteTask" -> {
                val title = args["title"]?.trim() ?: throw IllegalArgumentException("Task title is required")
                if (title.isBlank()) throw IllegalArgumentException("Task title cannot be blank")
                BuddyTool.DeleteTask(title = title)
            }

            "SaveMemory" -> {
                val content = args["content"]?.trim() ?: throw IllegalArgumentException("Memory content is required")
                if (content.isBlank()) throw IllegalArgumentException("Memory content cannot be blank")
                BuddyTool.SaveMemory(content = content)
            }

            "DeleteMemory" -> {
                val content = args["content"]?.trim() ?: throw IllegalArgumentException("Memory content is required")
                if (content.isBlank()) throw IllegalArgumentException("Memory content cannot be blank")
                BuddyTool.DeleteMemory(content = content)
            }

            else -> throw IllegalArgumentException("Unsupported tool: '$toolName'")
        }
    }
}
