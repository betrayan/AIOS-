package com.buddy.aios.core.domain.entity

import java.util.Locale

/**
 * Structured task and reminder categories for Buddy AI OS context awareness & priority intelligence.
 */
enum class TaskCategory {
    INTERVIEW,
    MEETING,
    APPOINTMENT,
    WORK,
    STUDY,
    PERSONAL,
    HEALTH,
    TRAVEL,
    PAYMENT,
    CALL,
    DEADLINE,
    GENERAL;

    companion object {
        fun fromText(title: String, description: String = ""): TaskCategory {
            val combined = "$title $description".lowercase(Locale.ENGLISH)
            return when {
                combined.contains("interview") -> INTERVIEW
                combined.contains("meeting") || combined.contains("sync") || combined.contains("standup") || combined.contains("catchup") || combined.contains("discussion") -> MEETING
                combined.contains("doctor") || combined.contains("appointment") || combined.contains("dentist") || combined.contains("clinic") || combined.contains("checkup") -> APPOINTMENT
                combined.contains("flight") || combined.contains("trip") || combined.contains("travel") || combined.contains("hotel") || combined.contains("train") || combined.contains("bus") -> TRAVEL
                combined.contains("pay") || combined.contains("bill") || combined.contains("electricity") || combined.contains("rent") || combined.contains("bank") || combined.contains("fee") -> PAYMENT
                combined.contains("call") || combined.contains("phone") || combined.contains("ring") -> CALL
                combined.contains("deadline") || combined.contains("submit") || combined.contains("due") -> DEADLINE
                combined.contains("study") || combined.contains("read") || combined.contains("exam") || combined.contains("course") || combined.contains("homework") -> STUDY
                combined.contains("gym") || combined.contains("medicine") || combined.contains("workout") || combined.contains("health") || combined.contains("pills") -> HEALTH
                combined.contains("code") || combined.contains("project") || combined.contains("task") || combined.contains("work") || combined.contains("bug") -> WORK
                else -> GENERAL
            }
        }

        fun inferPriority(category: TaskCategory): TaskPriority {
            return when (category) {
                INTERVIEW, APPOINTMENT, DEADLINE -> TaskPriority.HIGH
                MEETING, PAYMENT, TRAVEL -> TaskPriority.HIGH
                CALL, HEALTH -> TaskPriority.MEDIUM
                else -> TaskPriority.MEDIUM
            }
        }
    }
}
