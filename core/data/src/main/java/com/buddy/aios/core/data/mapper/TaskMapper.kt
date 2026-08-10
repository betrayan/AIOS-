package com.buddy.aios.core.data.mapper

import com.buddy.aios.core.database.entity.TaskEntity
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun TaskEntity.toDomain(): Task {
    val tagsList = try {
        json.decodeFromString<List<String>>(tagsJson)
    } catch (e: Exception) {
        emptyList()
    }
    val parsedPriority = try {
        TaskPriority.valueOf(priority)
    } catch (e: Exception) {
        TaskPriority.MEDIUM
    }

    return Task(
        id = id,
        title = title,
        description = description,
        isCompleted = isCompleted,
        createdAt = createdAt,
        dueDate = dueDate,
        reminderTime = reminderTime,
        priority = parsedPriority,
        tags = tagsList,
    )
}

fun Task.toEntity(): TaskEntity {
    val encodedTags = json.encodeToString(tags)
    return TaskEntity(
        id = id,
        title = title,
        description = description,
        isCompleted = isCompleted,
        createdAt = createdAt,
        dueDate = dueDate,
        reminderTime = reminderTime,
        priority = priority.name,
        tagsJson = encodedTags,
    )
}
