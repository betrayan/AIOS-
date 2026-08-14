package com.buddy.aios.core.domain.entity

/**
 * User preferences for Morning Briefing & Context Intelligence.
 */
data class MorningBriefingSettings(
    val isBriefingEnabled: Boolean = true,
    val isVoiceEnabled: Boolean = true,
    val includeWeather: Boolean = true,
    val includeSleep: Boolean = true,
    val contextualBatteryAlerts: Boolean = true,
    val contextualTravelAlerts: Boolean = true,
    val importantReminderVoice: Boolean = true,
    val allowScreenOffVoice: Boolean = false,
)
