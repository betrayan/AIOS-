package com.buddy.aios.core.domain.entity

/**
 * Domain entity representing the user's profile and preferences.
 */
data class UserProfile(
    val id: String,
    val name: String,
    val preferredName: String,
    val personaPreference: String,
    val languageCode: String = "en",
    val privacyLevel: PrivacyLevel = PrivacyLevel.LOCAL_ONLY,
    val onboardingCompleted: Boolean = false,
)

/**
 * Controls where AI processing happens.
 * - LOCAL_ONLY: All AI inference on-device; no data leaves the device.
 * - CLOUD_OPT_IN: User has explicitly consented to cloud AI processing.
 */
enum class PrivacyLevel(val level: Int) {
    LOCAL_ONLY(0),
    CLOUD_OPT_IN(1),
}
