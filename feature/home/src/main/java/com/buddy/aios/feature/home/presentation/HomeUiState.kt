package com.buddy.aios.feature.home.presentation

import com.buddy.aios.core.ai.morning.MorningSummary
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Conversation
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.entity.Task

data class HomeUiState(
    val userGreeting: String = "Good Morning",
    val userName: String = "Vijay",
    val subtitleText: String = "Here's what matters today.",
    val buddyMode: BuddyMode = BuddyMode.ACTIVE,
    val privacyLevel: PrivacyLevel = PrivacyLevel.LOCAL_ONLY,
    val conversations: List<Conversation> = emptyList(),
    val activeTasks: List<Task> = emptyList(),
    val completedTasksCount: Int = 0,
    val topPriorityTask: Task? = null,
    val reminderCount: Int = 0,
    val eventCount: Int = 1,
    val weatherTemp: String = "28°",
    val weatherCondition: String = "Clear",
    val memoryCount: Int = 0,
    val morningSummary: MorningSummary? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
