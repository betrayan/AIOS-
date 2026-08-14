package com.buddy.aios.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["is_completed"]),
        Index(value = ["due_date"]),
        Index(value = ["reminder_time"]),
        Index(value = ["status"]),
    ],
)
data class TaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "due_date")
    val dueDate: Long? = null,

    @ColumnInfo(name = "reminder_time")
    val reminderTime: Long? = null,

    @ColumnInfo(name = "priority")
    val priority: String = "MEDIUM",

    @ColumnInfo(name = "tags_json")
    val tagsJson: String = "[]",

    @ColumnInfo(name = "is_reminder")
    val isReminder: Boolean = false,

    @ColumnInfo(name = "notification_id")
    val notificationId: Int = 0,

    @ColumnInfo(name = "timezone")
    val timezone: String = "UTC",

    @ColumnInfo(name = "status")
    val status: String = "PENDING",

    @ColumnInfo(name = "recurrence_rule")
    val recurrenceRule: String? = null,

    @ColumnInfo(name = "delivery_state", defaultValue = "SCHEDULED")
    val deliveryState: String = "SCHEDULED",

    @ColumnInfo(name = "voice_enabled", defaultValue = "1")
    val voiceEnabled: Boolean = true,

    @ColumnInfo(name = "notification_enabled", defaultValue = "1")
    val notificationEnabled: Boolean = true,

    @ColumnInfo(name = "morning_eligible", defaultValue = "1")
    val morningEligible: Boolean = true,
)
