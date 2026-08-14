package com.buddy.aios.core.ai.brain

import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority
import javax.inject.Inject
import javax.inject.Singleton

data class RankedTask(
    val task: Task,
    val priorityLevel: PriorityLevel,
    val urgencyReason: String,
)

/**
 * Stage 8 Priority Evaluation Engine.
 * Evaluates tasks and actions into CRITICAL, HIGH, MEDIUM, LOW, BACKGROUND.
 * Ensures travel leaving in 30 minutes ranks higher than routine homework.
 */
@Singleton
class PriorityEngine @Inject constructor() {

    fun rankTask(task: Task, now: Long = System.currentTimeMillis()): RankedTask {
        val title = task.title.lowercase()
        val isTravel = title.contains("travel") || title.contains("flight") || title.contains("train") || title.contains("leaving")
        val reminderTime = task.reminderTime ?: task.dueDate ?: 0L
        val timeDiffMs = if (reminderTime > 0) reminderTime - now else Long.MAX_VALUE

        return when {
            // 1. CRITICAL: Travel departure within 2 hours or urgent reminder within 15 mins
            isTravel && timeDiffMs in 1..(2 * 3600 * 1000L) -> RankedTask(
                task = task,
                priorityLevel = PriorityLevel.CRITICAL,
                urgencyReason = "Imminent travel departure"
            )
            timeDiffMs in 1..(15 * 60 * 1000L) -> RankedTask(
                task = task,
                priorityLevel = PriorityLevel.CRITICAL,
                urgencyReason = "Reminder due in less than 15 minutes"
            )
            // 2. HIGH: Overdue tasks or High domain priority tasks
            reminderTime in 1..<now -> RankedTask(
                task = task,
                priorityLevel = PriorityLevel.HIGH,
                urgencyReason = "Task is overdue"
            )
            task.priority == TaskPriority.HIGH || isTravel -> RankedTask(
                task = task,
                priorityLevel = PriorityLevel.HIGH,
                urgencyReason = "High priority marked task"
            )
            // 3. MEDIUM: Tasks due today
            timeDiffMs in 1..(24 * 3600 * 1000L) -> RankedTask(
                task = task,
                priorityLevel = PriorityLevel.MEDIUM,
                urgencyReason = "Scheduled for today"
            )
            // 4. LOW / BACKGROUND: General future tasks
            else -> RankedTask(
                task = task,
                priorityLevel = PriorityLevel.LOW,
                urgencyReason = "General task"
            )
        }
    }

    fun selectTopPriorities(tasks: List<Task>, maxItems: Int = 3): List<RankedTask> {
        val now = System.currentTimeMillis()
        return tasks.map { rankTask(it, now) }
            .sortedBy { ranked ->
                when (ranked.priorityLevel) {
                    PriorityLevel.CRITICAL -> 1
                    PriorityLevel.HIGH -> 2
                    PriorityLevel.MEDIUM -> 3
                    PriorityLevel.LOW -> 4
                    PriorityLevel.BACKGROUND -> 5
                }
            }
            .take(maxItems)
    }
}
