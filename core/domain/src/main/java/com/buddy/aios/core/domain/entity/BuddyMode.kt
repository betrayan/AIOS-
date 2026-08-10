package com.buddy.aios.core.domain.entity

/**
 * The four operation modes of Buddy AI OS.
 *
 * State behavior rules:
 * - [ACTIVE]: Normal companion operation. Proactive messages, voice I/O, background AI workers allowed.
 * - [QUIET]: No unsolicited/proactive messages. Explicit user interactions respond normally. Voice available. Reminders/tasks run.
 * - [SILENT]: Spoken voice input/output disabled. Text interaction & reminders active. Proactive messages disabled.
 * - [OFF]: Buddy background AI activity completely disabled. Non-essential background workers halt execution.
 */
enum class BuddyMode {
    ACTIVE,
    QUIET,
    SILENT,
    OFF,
}

/**
 * Centralized capability policy layer for Buddy AI OS capabilities.
 *
 * Components across the system MUST query capabilities from this policy object or [IBuddyModeRepository]
 * rather than hardcoding raw `if (mode == BuddyMode.ACTIVE)` comparisons.
 */
data class BuddyCapability(
    val allowProactiveConversation: Boolean,
    val allowVoiceInputOutput: Boolean,
    val allowAiBackgroundProcessing: Boolean,
    val allowUserRemindersAndTasks: Boolean,
    val allowTextInteraction: Boolean,
)

fun BuddyMode.getCapabilities(): BuddyCapability {
    return when (this) {
        BuddyMode.ACTIVE -> BuddyCapability(
            allowProactiveConversation = true,
            allowVoiceInputOutput = true,
            allowAiBackgroundProcessing = true,
            allowUserRemindersAndTasks = true,
            allowTextInteraction = true,
        )
        BuddyMode.QUIET -> BuddyCapability(
            allowProactiveConversation = false,
            allowVoiceInputOutput = true,
            allowAiBackgroundProcessing = true,
            allowUserRemindersAndTasks = true,
            allowTextInteraction = true,
        )
        BuddyMode.SILENT -> BuddyCapability(
            allowProactiveConversation = false,
            allowVoiceInputOutput = false,
            allowAiBackgroundProcessing = true,
            allowUserRemindersAndTasks = true,
            allowTextInteraction = true,
        )
        BuddyMode.OFF -> BuddyCapability(
            allowProactiveConversation = false,
            allowVoiceInputOutput = false,
            allowAiBackgroundProcessing = false,
            allowUserRemindersAndTasks = true, // User-critical scheduled alarms/reminders remain allowed
            allowTextInteraction = false,
        )
    }
}
