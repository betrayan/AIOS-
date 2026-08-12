package com.buddy.aios.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.aios.core.ai.morning.MorningContextEngine
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IMemoryRepository
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

            HomeUiState(
                userGreeting = morningSummary.greeting,
                buddyMode = mode,
                privacyLevel = userProfile.privacyLevel,
                conversations = conversations,
                activeTasks = tasks,
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

    fun onCreateQuickTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val task = Task(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                createdAt = System.currentTimeMillis(),
                priority = TaskPriority.MEDIUM,
            )
            taskRepository.saveTask(task)
        }
    }
}
