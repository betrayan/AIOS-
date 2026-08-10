package com.buddy.aios.feature.home.presentation

import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Conversation
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.entity.Task

data class HomeUiState(
    val userGreeting: String = "Hello!",
    val buddyMode: BuddyMode = BuddyMode.ACTIVE,
    val privacyLevel: PrivacyLevel = PrivacyLevel.LOCAL_ONLY,
    val conversations: List<Conversation> = emptyList(),
    val activeTasks: List<Task> = emptyList(),
    val memoryCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
