package com.buddy.aios.core.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.aios.core.domain.result.AppError
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel class providing standardized coroutine exception handling and UI single-event flow.
 */
abstract class BaseViewModel : ViewModel() {

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow: SharedFlow<UiEvent> = _eventFlow.asSharedFlow()

    protected val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        handleError(AppError.fromException(Exception(throwable)))
    }

    protected fun emitEvent(event: UiEvent) {
        viewModelScope.launch {
            _eventFlow.emit(event)
        }
    }

    protected open fun handleError(error: AppError) {
        emitEvent(UiEvent.ShowToast(error.toString()))
    }
}

/**
 * One-off UI events (e.g. snackbars, toasts, navigation commands).
 */
sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
    data class ShowSnackbar(val message: String) : UiEvent
    data class Navigate(val route: String) : UiEvent
}
