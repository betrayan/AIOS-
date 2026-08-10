package com.buddy.aios.feature.memory.presentation

import com.buddy.aios.core.domain.entity.Memory

data class MemoryUiState(
    val memories: List<Memory> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
