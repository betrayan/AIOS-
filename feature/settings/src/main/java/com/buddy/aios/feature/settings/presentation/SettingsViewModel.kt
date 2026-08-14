package com.buddy.aios.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.MorningBriefingSettings
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.repository.IMorningBriefingSettingsRepository
import com.buddy.aios.core.domain.repository.IReminderEngine
import com.buddy.aios.core.domain.repository.ITaskRepository
import com.buddy.aios.core.domain.repository.IUserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val buddyModeRepository: IBuddyModeRepository,
    private val userRepository: IUserRepository,
    private val memoryRepository: IMemoryRepository,
    private val taskRepository: ITaskRepository,
    private val reminderEngine: IReminderEngine,
    private val morningSettingsRepository: IMorningBriefingSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        combine(
            buddyModeRepository.observeBuddyMode(),
            userRepository.observeUserProfile(),
            morningSettingsRepository.observeSettings(),
        ) { mode, profile, morningSettings ->
            SettingsUiState(
                userProfile = profile,
                buddyMode = mode,
                morningSettings = morningSettings,
                isLoading = false,
                errorMessage = null,
            )
        }
            .catch { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load settings",
                )
            }
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    fun onSetBuddyMode(mode: BuddyMode) {
        viewModelScope.launch {
            buddyModeRepository.setBuddyMode(mode)
        }
    }

    fun onSetPrivacyLevel(privacyLevel: PrivacyLevel) {
        viewModelScope.launch {
            userRepository.setPrivacyLevel(privacyLevel)
        }
    }

    fun onUpdateUserName(name: String) {
        viewModelScope.launch {
            val current = _uiState.value.userProfile
            userRepository.updateUserProfile(current.copy(name = name, preferredName = name))
        }
    }

    fun onUpdateMorningSettings(settings: MorningBriefingSettings) {
        viewModelScope.launch {
            morningSettingsRepository.updateSettings(settings)
        }
    }

    fun onPruneMemories() {
        viewModelScope.launch {
            memoryRepository.pruneExpiredMemories(System.currentTimeMillis(), 0.1f)
        }
    }

    fun onScheduleTestReminder(onScheduled: () -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            reminderEngine.createReminder(
                title = "AIOS Test Reminder",
                description = "Verification reminder triggered after 60 seconds",
                triggerTimestamp = now + 60_000L,
                recurrenceRule = null,
                voiceEnabled = true,
            )
            onScheduled()
        }
    }
}
