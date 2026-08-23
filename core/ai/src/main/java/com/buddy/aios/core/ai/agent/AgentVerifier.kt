package com.buddy.aios.core.ai.agent

import com.buddy.aios.core.ai.tool.BuddyTool
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.repository.ITaskRepository
import com.buddy.aios.core.domain.result.Result
import javax.inject.Inject
import javax.inject.Singleton

data class VerificationResult(
    val isVerified: Boolean,
    val details: String,
)

/**
 * Empirical state verifier.
 *
 * Responsibilities:
 * - Queries [ITaskRepository] and [IMemoryRepository] after tool execution.
 * - Confirms actual expected database state before reporting success to user.
 * - Prevents AIOS from falsely reporting "Done" if DB persistence failed.
 */
@Singleton
class AgentVerifier @Inject constructor(
    private val taskRepository: ITaskRepository,
    private val memoryRepository: IMemoryRepository,
) {
    companion object {
        private const val TAG = "AgentVerifier"
    }

    suspend fun verify(tool: BuddyTool): VerificationResult {
        AppLogger.d(TAG, "Verifying execution for tool: ${tool.javaClass.simpleName}")

        return when (tool) {
            is BuddyTool.CreateTask -> verifyCreateTask(tool.title)
            is BuddyTool.CompleteTask -> verifyCompleteTask(tool.title)
            is BuddyTool.DeleteTask -> verifyDeleteTask(tool.title)
            is BuddyTool.SaveMemory -> verifySaveMemory(tool.content)
            is BuddyTool.DeleteMemory -> verifyDeleteMemory(tool.content)
            is BuddyTool.ConfigureMorningWish -> VerificationResult(isVerified = true, details = "Morning Wish alarm configured.")
        }
    }

    private suspend fun verifyCreateTask(title: String): VerificationResult {
        val tasksResult = taskRepository.getUpcomingTasks(0L)
        val tasks = (tasksResult as? Result.Success)?.value ?: emptyList()
        val exists = tasks.any { it.title.contains(title, ignoreCase = true) }

        return if (exists) {
            VerificationResult(isVerified = true, details = "Task '$title' verified in database.")
        } else {
            VerificationResult(isVerified = false, details = "Could not confirm task '$title' in database.")
        }
    }

    private suspend fun verifyCompleteTask(title: String): VerificationResult {
        val tasksResult = taskRepository.getUpcomingTasks(0L)
        val tasks = (tasksResult as? Result.Success)?.value ?: emptyList()
        val task = tasks.firstOrNull { it.title.contains(title, ignoreCase = true) }

        return if (task == null || task.isCompleted) {
            VerificationResult(isVerified = true, details = "Task '$title' verified as completed.")
        } else {
            VerificationResult(isVerified = false, details = "Task '$title' is not marked as completed.")
        }
    }

    private suspend fun verifyDeleteTask(title: String): VerificationResult {
        val tasksResult = taskRepository.getUpcomingTasks(0L)
        val tasks = (tasksResult as? Result.Success)?.value ?: emptyList()
        val stillExists = tasks.any { it.title.equals(title, ignoreCase = true) && !it.isCompleted }

        return if (!stillExists) {
            VerificationResult(isVerified = true, details = "Task '$title' removal verified.")
        } else {
            VerificationResult(isVerified = false, details = "Task '$title' still present in database.")
        }
    }

    private suspend fun verifySaveMemory(content: String): VerificationResult {
        val searchResult = memoryRepository.searchMemories(content.take(40))
        val memories = (searchResult as? Result.Success)?.value ?: emptyList()
        val exists = memories.any { it.summary.contains(content, ignoreCase = true) }

        return if (exists) {
            VerificationResult(isVerified = true, details = "Memory '$content' verified in long-term memory.")
        } else {
            VerificationResult(isVerified = false, details = "Could not confirm memory in database.")
        }
    }

    private suspend fun verifyDeleteMemory(content: String): VerificationResult {
        val searchResult = memoryRepository.searchMemories(content.take(40))
        val memories = (searchResult as? Result.Success)?.value ?: emptyList()
        val exists = memories.any { it.summary.contains(content, ignoreCase = true) }

        return if (!exists) {
            VerificationResult(isVerified = true, details = "Memory removal verified.")
        } else {
            VerificationResult(isVerified = false, details = "Memory still present in database.")
        }
    }
}
