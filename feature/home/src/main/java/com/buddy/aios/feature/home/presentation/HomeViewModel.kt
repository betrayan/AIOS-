package com.buddy.aios.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.aios.core.ai.morning.MorningContextEngine
import com.buddy.aios.core.common.time.NaturalLanguageTimeParser
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.repository.IReminderEngine
import com.buddy.aios.core.domain.repository.ITaskRepository
import com.buddy.aios.core.domain.repository.IUserRepository
import com.buddy.aios.core.domain.result.Result
import com.buddy.aios.core.domain.usecase.CreateConversationUseCase
import com.buddy.aios.core.domain.usecase.ObserveConversationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeConversationsUseCase: ObserveConversationsUseCase,
    private val createConversationUseCase: CreateConversationUseCase,
    private val buddyModeRepository: IBuddyModeRepository,
    private val taskRepository: ITaskRepository,
    private val memoryRepository: IMemoryRepository,
    private val userRepository: IUserRepository,
    private val morningContextEngine: MorningContextEngine,
    private val priorityEngine: com.buddy.aios.core.ai.brain.PriorityEngine,
    private val reminderEngine: IReminderEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        combine(
            observeConversationsUseCase(),
            buddyModeRepository.observeBuddyMode(),
            taskRepository.observeActiveTasks(),
            memoryRepository.observeMemories(),
            userRepository.observeUserProfile(),
        ) { conversations, mode, tasks, memories, userProfile ->
            val morningSummary = morningContextEngine.generateMorningSummary(
                userProfile = userProfile,
                activeTasks = tasks,
                memories = memories,
            )

            val topTask = priorityEngine.selectTopPriorities(tasks, maxItems = 1).firstOrNull()?.task
            val reminders = tasks.filter { it.isReminder || it.reminderTime != null }
            val name = userProfile?.preferredName?.ifBlank { userProfile.name } ?: "Vijay"

            val subtitle = when {
                tasks.size >= 5 -> "You have a busy day ahead."
                tasks.isNotEmpty() -> "Ready to make today productive?"
                else -> "Your day looks pretty light."
            }

            HomeUiState(
                userGreeting = morningSummary.greeting,
                userName = name,
                subtitleText = subtitle,
                buddyMode = mode,
                privacyLevel = userProfile?.privacyLevel ?: com.buddy.aios.core.domain.entity.PrivacyLevel.LOCAL_ONLY,
                conversations = conversations,
                activeTasks = tasks,
                topPriorityTask = topTask,
                reminderCount = reminders.size,
                memoryCount = memories.size,
                morningSummary = morningSummary,
                isLoading = false,
                errorMessage = null,
            )
        }
            .catch { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load dashboard data",
                )
            }
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    fun onNewConversation(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val result = createConversationUseCase(personaId = "default")
            when (result) {
                is Result.Success -> onCreated(result.value)
                is Result.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.error.toString())
            }
        }
    }

    fun onSetBuddyMode(mode: BuddyMode) {
        viewModelScope.launch {
            buddyModeRepository.setBuddyMode(mode)
        }
    }

    fun onCompleteTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.completeTask(taskId, isCompleted = true)
        }
    }

    /**
     * Creates a reminder with a specific date+time from the Add Reminder UI.
     * Routes through [IReminderEngine] which sets all reminder fields correctly:
     * voiceEnabled=true, isReminder=true, timezone=device, notificationId=uuid.hashCode().
     */
    fun onCreateReminder(
        title: String,
        description: String = "",
        reminderTimeMs: Long,
        priority: TaskPriority = TaskPriority.MEDIUM,
        voiceEnabled: Boolean = true,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            reminderEngine.createReminder(
                title = title.trim(),
                description = description.trim(),
                triggerTimestamp = reminderTimeMs,
                voiceEnabled = voiceEnabled,
            )
        }
    }

    /**
     * Creates a quick task from a text title.
     * If the title contains a time expression ("at 7 PM", "in 30 minutes", etc.),
     * routes through [IReminderEngine] to schedule an exact alarm.
     * Otherwise creates a plain task with no alarm.
     */
    fun onCreateQuickTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val parsed = NaturalLanguageTimeParser.parse(title.trim(), now)
            val parsedTime = parsed.timestamp // local val enables smart cast across module boundary
            if (parsedTime != null && parsedTime > now) {
                // Natural language time found — create a proper scheduled reminder
                reminderEngine.createReminder(
                    title = title.trim(),
                    description = "",
                    triggerTimestamp = parsedTime,
                    voiceEnabled = true,
                )
            } else {
                // No time found — create a plain unscheduled task
                val task = Task(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    createdAt = now,
                    priority = TaskPriority.MEDIUM,
                )
                taskRepository.saveTask(task)
            }
        }
    }
}
