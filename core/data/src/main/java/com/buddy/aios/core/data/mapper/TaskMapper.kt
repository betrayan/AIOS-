package com.buddy.aios.core.data.mapper

import com.buddy.aios.core.database.entity.TaskEntity
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority
import com.buddy.aios.core.domain.entity.TaskStatus
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
    val parsedStatus = try {
        TaskStatus.valueOf(status)
    } catch (e: Exception) {
        if (isCompleted) TaskStatus.COMPLETED else TaskStatus.PENDING
    }

    val parsedDeliveryState = try {
        com.buddy.aios.core.domain.entity.ReminderDeliveryState.valueOf(deliveryState)
    } catch (e: Exception) {
        if (isCompleted) com.buddy.aios.core.domain.entity.ReminderDeliveryState.COMPLETED else com.buddy.aios.core.domain.entity.ReminderDeliveryState.SCHEDULED
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
        isReminder = isReminder,
        notificationId = if (notificationId != 0) notificationId else id.hashCode(),
        timezone = timezone,
        status = parsedStatus,
        recurrenceRule = recurrenceRule,
        deliveryState = parsedDeliveryState,
        voiceEnabled = voiceEnabled,
        notificationEnabled = notificationEnabled,
        morningBriefingEligible = morningEligible,
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
        isReminder = isReminder,
        notificationId = notificationId,
        timezone = timezone,
        status = status.name,
        recurrenceRule = recurrenceRule,
        deliveryState = deliveryState.name,
        voiceEnabled = voiceEnabled,
        notificationEnabled = notificationEnabled,
        morningEligible = morningBriefingEligible,
    )
}
