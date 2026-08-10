package com.buddy.aios.core.ui.state

/**
 * Universal UI state wrapper used by all ViewModels.
 * Drives the Compose UI layer via collectAsStateWithLifecycle().
 */
sealed interface UiState<out T> {
    /** Loading/initial state — show progress indicator. */
    data object Loading : UiState<Nothing>

    /** Content is ready — render [data]. */
    data class Success<T>(val data: T) : UiState<T>

    /** A recoverable error — show error message with retry option. */
    data class Error(
        val message: String,
        val canRetry: Boolean = true,
    ) : UiState<Nothing>

    /** Empty state — no data to show (different from loading). */
    data object Empty : UiState<Nothing>
}

/** Convenience extensions for concise ViewModel code. */
fun <T> UiState<T>.dataOrNull(): T? = (this as? UiState.Success)?.data
fun <T> UiState<T>.isLoading(): Boolean = this is UiState.Loading
