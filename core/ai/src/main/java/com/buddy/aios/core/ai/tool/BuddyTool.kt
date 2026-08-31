package com.buddy.aios.core.ai.tool

import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority

/**
 * Sealed hierarchy representing all operations Buddy can perform on behalf of the user.
 *
 * The AI never directly modifies Room or DataStore.
 * Tools are validated and executed through Use Cases by [ToolExecutor].
 */
sealed interface BuddyTool {

    /** Create a new task or reminder. */
    data class CreateTask(
        val title: String,
        val description: String = "",
        val dueTimestamp: Long? = null,
        val priority: TaskPriority = TaskPriority.MEDIUM,
    ) : BuddyTool

    /** Mark an existing task as completed by fuzzy title match. */
    data class CompleteTask(
        val title: String,
    ) : BuddyTool

    /** Delete a task by fuzzy title match. */
    data class DeleteTask(
        val title: String,
    ) : BuddyTool

    /** Save a long-term memory about the user. */
    data class SaveMemory(
        val content: String,
        val importance: Float = 0.7f,
        val tags: List<String> = emptyList(),
    ) : BuddyTool

    /** Delete a specific memory by content match. */
    data class DeleteMemory(
        val content: String,
    ) : BuddyTool

    /** Configure or update the daily Morning Wish alarm time. */
    data class ConfigureMorningWish(
        val hour: Int,
        val minute: Int,
        val isEnabled: Boolean = true,
    ) : BuddyTool
}

/** Result of a [BuddyTool] execution. */
sealed interface ToolResult {
    data class Success(val confirmation: String) : ToolResult
    data class Failure(val reason: String) : ToolResult
    data object ValidationError : ToolResult
}

/**
 * Maps a [BuddyTool.CreateTask] to a [Task] domain entity.
 *
 * Key contract:
 * - [Task.isReminder] is automatically `true` when [dueTimestamp] is non-null (domain default).
 * - [Task.voiceEnabled] defaults to `true` — TTS fires on reminder delivery.
 * - [Task.notificationId] defaults to `id.hashCode()` — unique per task, no collisions.
 * - [Task.timezone] defaults to the device timezone — correct locale for alarm display.
 * - [Task.morningBriefingEligible] defaults to `true` — surfaces in Morning Wish.
 */
fun BuddyTool.CreateTask.toDomainTask(now: Long = System.currentTimeMillis()): Task {
    val taskId = java.util.UUID.randomUUID().toString()
    return Task(
        id = taskId,
        title = title,
        description = description,
        isCompleted = false,
        createdAt = now,
        dueDate = dueTimestamp,
        reminderTime = dueTimestamp,
        priority = priority,
        // All other fields (isReminder, voiceEnabled, notificationId, timezone,
        // notificationEnabled, morningBriefingEligible) use correct Task defaults.
    )
}
