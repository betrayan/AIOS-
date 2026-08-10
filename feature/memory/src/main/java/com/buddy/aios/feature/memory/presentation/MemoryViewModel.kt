package com.buddy.aios.feature.memory.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.result.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: IMemoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        memoryRepository.observeMemories()
            .onEach { allMemories ->
                val query = _searchQuery.value.trim().lowercase()
                val filtered = if (query.isBlank()) {
                    allMemories
                } else {
                    allMemories.filter { it.summary.lowercase().contains(query) }
                }
                _uiState.value = _uiState.value.copy(
                    memories = filtered,
                    isLoading = false,
                    errorMessage = null,
                )
            }
            .catch { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to observe memories",
                )
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onDeleteMemory(memoryId: String) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(memoryId)
        }
    }

    fun onUpdateMemory(memory: Memory) {
        viewModelScope.launch {
            memoryRepository.updateMemory(memory)
        }
    }
}
