package com.buddy.aios.core.ai.tool

import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.repository.IReminderScheduler
import com.buddy.aios.core.domain.repository.ITaskRepository
import com.buddy.aios.core.domain.result.Result
import com.buddy.aios.core.domain.repository.IMorningBriefingSettingsRepository
import com.buddy.aios.core.domain.repository.IMorningWishEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolExecutor @Inject constructor(
    private val taskRepository: ITaskRepository,
    private val memoryRepository: IMemoryRepository,
    private val reminderScheduler: IReminderScheduler,
    private val morningSettingsRepository: IMorningBriefingSettingsRepository,
    private val morningWishEngine: IMorningWishEngine,
) {
    companion object {
        private const val TAG = "ToolExecutor"
    }

    suspend fun execute(tool: BuddyTool): ToolResult {
        return when (tool) {
            is BuddyTool.CreateTask -> createTask(tool)
            is BuddyTool.CompleteTask -> completeTask(tool)
            is BuddyTool.DeleteTask -> deleteTask(tool)
            is BuddyTool.SaveMemory -> saveMemory(tool)
            is BuddyTool.DeleteMemory -> deleteMemory(tool)
            is BuddyTool.ConfigureMorningWish -> configureMorningWish(tool)
        }
    }

    private suspend fun createTask(tool: BuddyTool.CreateTask): ToolResult {
        if (tool.title.isBlank()) return ToolResult.ValidationError
        val task = tool.toDomainTask()

        return when (val result = taskRepository.saveTask(task)) {
            is Result.Success -> {
                AppLogger.d(TAG, "Task created: '${tool.title}' (dueTimestamp=${tool.dueTimestamp})")
                val timeConfirmation = if (tool.dueTimestamp != null && tool.dueTimestamp > 0L) {
                    val formatted = com.buddy.aios.core.common.time.ReminderDateFormatter.formatNaturalDateTime(tool.dueTimestamp)
                    " $formatted"
                } else ""

                val permissionNote = if (tool.dueTimestamp != null && !reminderScheduler.canScheduleExactAlarms()) {
                    " (Note: Enable 'Alarms & reminders' in Settings for exact-time alerts)"
                } else ""

                ToolResult.Success("Done. I'll remind you to ${tool.title}$timeConfirmation.$permissionNote")
            }
            is Result.Error -> {
                AppLogger.e(TAG, "Failed to create task: ${result.error}")
                ToolResult.Failure("Could not save the task: ${result.error}")
            }
        }
    }

    private suspend fun completeTask(tool: BuddyTool.CompleteTask): ToolResult {
        if (tool.title.isBlank()) return ToolResult.ValidationError
        val upcoming = taskRepository.getUpcomingTasks(0L)
        val task = (upcoming as? Result.Success)?.value
            ?.firstOrNull { it.title.contains(tool.title, ignoreCase = true) }
            ?: return ToolResult.Failure("No task found matching '${tool.title}'.")

        return when (val result = taskRepository.completeTask(task.id, true)) {
            is Result.Success -> {
                AppLogger.d(TAG, "Task completed: '${task.title}'")
                ToolResult.Success("Done. Marked '${task.title}' as completed.")
            }
            is Result.Error -> ToolResult.Failure("Could not complete the task.")
        }
    }

    private suspend fun deleteTask(tool: BuddyTool.DeleteTask): ToolResult {
        if (tool.title.isBlank()) return ToolResult.ValidationError
        val upcoming = taskRepository.getUpcomingTasks(0L)
        val task = (upcoming as? Result.Success)?.value
            ?.firstOrNull { it.title.contains(tool.title, ignoreCase = true) }
            ?: return ToolResult.Failure("No task found matching '${tool.title}'.")

        return when (val result = taskRepository.deleteTask(task.id)) {
            is Result.Success -> ToolResult.Success("Done. I've deleted your '${task.title}' reminder.")
            is Result.Error -> ToolResult.Failure("Could not delete the task.")
        }
    }

    private suspend fun saveMemory(tool: BuddyTool.SaveMemory): ToolResult {
        if (tool.content.isBlank()) return ToolResult.ValidationError
        val now = System.currentTimeMillis()
        val memory = Memory(
            id = UUID.randomUUID().toString(),
            userId = "local",
            summary = tool.content,
            sourceConversationId = null,
            importance = tool.importance,
            createdAt = now,
            lastAccessedAt = now,
            expiresAt = null,
            tags = tool.tags,
        )
        return when (val result = memoryRepository.saveMemory(memory)) {
            is Result.Success -> {
                AppLogger.d(TAG, "Memory saved: '${tool.content}'")
                ToolResult.Success("I'll remember that.")
            }
            is Result.Error -> ToolResult.Failure("Could not save that memory.")
        }
    }

    private suspend fun deleteMemory(tool: BuddyTool.DeleteMemory): ToolResult {
        if (tool.content.isBlank()) return ToolResult.ValidationError
        val memories = memoryRepository.searchMemories(tool.content)
        val memory = (memories as? Result.Success)?.value?.firstOrNull()
            ?: return ToolResult.Failure("No memory found matching '${tool.content}'.")

        return when (val result = resultRepositoryDelete(memory.id)) {
            is Result.Success -> ToolResult.Success("I've forgotten that.")
            is Result.Error -> ToolResult.Failure("Could not remove that memory.")
        }
    }

    private suspend fun configureMorningWish(tool: BuddyTool.ConfigureMorningWish): ToolResult {
        return try {
            val currentSettings = morningSettingsRepository.getSettings()
            morningSettingsRepository.updateSettings(
                currentSettings.copy(
                    isMorningWishEnabled = tool.isEnabled,
                    morningWishHour = tool.hour,
                    morningWishMinute = tool.minute,
                )
            )
            morningWishEngine.scheduleMorningWish()

            val formattedTime = String.format(
                Locale.ENGLISH, "%d:%02d %s",
                if (tool.hour % 12 == 0) 12 else tool.hour % 12,
                tool.minute,
                if (tool.hour >= 12) "PM" else "AM"
            )
            AppLogger.d(TAG, "Morning Wish updated to $formattedTime (hour=${tool.hour}, minute=${tool.minute}, enabled=${tool.isEnabled})")
            ToolResult.Success("Got it, Vijay! I've set your Morning Wish alarm for $formattedTime every morning.")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error updating Morning Wish settings", e)
            ToolResult.Failure("Could not update Morning Wish settings.")
        }
    }

    private suspend fun resultRepositoryDelete(id: String) = memoryRepository.deleteMemory(id)
}
