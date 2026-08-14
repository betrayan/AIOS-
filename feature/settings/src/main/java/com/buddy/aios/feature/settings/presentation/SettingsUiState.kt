package com.buddy.aios.feature.settings.presentation

import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.MorningBriefingSettings
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.entity.UserProfile

data class SettingsUiState(
    val userProfile: UserProfile = UserProfile("local_user", "Buddy User", "Friend", "companion"),
    val buddyMode: BuddyMode = BuddyMode.ACTIVE,
    val isVoiceEnabled: Boolean = true,
    val morningSettings: MorningBriefingSettings = MorningBriefingSettings(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
