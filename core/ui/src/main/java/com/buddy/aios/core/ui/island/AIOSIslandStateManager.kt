package com.buddy.aios.core.ui.island

import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AIOS Dynamic Island State Manager.
 *
 * Responsibilities:
 * - Holds the single observable [StateFlow] of the current island display state.
 * - Exposes [show], [update], and [dismiss] to all components that drive the island.
 * - Respects BuddyMode: in SILENT or OFF mode, non-critical events are suppressed.
 * - Handles auto-dismiss timers internally so callers don't need to manage coroutines.
 *
 * Components that read the state: [AIOSIsland] composable (drawn in MainActivity).
 * Components that write the state: ChatViewModel, future notification workers.
 */
@Singleton
class AIOSIslandStateManager @Inject constructor(
    private val buddyModeRepository: IBuddyModeRepository,
) {
    companion object {
        private const val TAG = "AIOSIslandStateManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _displayState = MutableStateFlow(AIOSIslandDisplayState())
    val displayState: StateFlow<AIOSIslandDisplayState> = _displayState.asStateFlow()

    private var autoDismissJob: Job? = null

    private fun getPriority(state: AIOSIslandState): Int {
        return when (state) {
            AIOSIslandState.REMINDER,
            AIOSIslandState.MORNING_WISH,
            AIOSIslandState.TASK_CREATED,
            AIOSIslandState.MEMORY_SAVED,
            AIOSIslandState.ERROR -> 3

            AIOSIslandState.CONTINUOUS,
            AIOSIslandState.LISTENING,
            AIOSIslandState.SPEAKING,
            AIOSIslandState.THINKING,
            AIOSIslandState.TOOL_EXECUTION -> 2

            AIOSIslandState.STOPPING,
            AIOSIslandState.AIOS_MESSAGE -> 1

            AIOSIslandState.IDLE -> 0
        }
    }

    /**
     * Shows the Dynamic Island with the given [state] and [message].
     *
     * @param state          The visual state (THINKING, TASK_CREATED, etc.)
     * @param message        Short text for the compact pill.
     * @param autoDismissMs  If > 0, automatically dismisses after this duration. 0 = manual dismiss.
     * @param actionLabel    Optional label shown in expanded state.
     * @param onAction       Callback when action button tapped.
     */
    fun show(
        state: AIOSIslandState,
        message: String,
        autoDismissMs: Long = 0L,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        scope.launch {
            val buddyMode = buddyModeRepository.getBuddyMode()
            if (!shouldShow(state, buddyMode)) {
                AppLogger.d(TAG, "Island suppressed by BuddyMode: state=$state, buddyMode=$buddyMode")
                return@launch
            }

            val current = _displayState.value
            if (current.isVisible && getPriority(state) < getPriority(current.state)) {
                AppLogger.d(TAG, "Island change rejected due to state priority: active=${current.state} (priority ${getPriority(current.state)}), attempted=$state (priority ${getPriority(state)})")
                return@launch
            }

            cancelAutoDismiss()
            AppLogger.d(TAG, "DynamicIsland: ${current.state} → $state ('$message')")
            _displayState.value = AIOSIslandDisplayState(
                state = state,
                message = message,
                isVisible = true,
                autoDismissMs = autoDismissMs,
                actionLabel = actionLabel,
                onAction = onAction,
            )

            if (autoDismissMs > 0L) {
                scheduleAutoDismiss(autoDismissMs)
            }
        }
    }

    /**
     * Updates the current island state without changing visibility or message.
     * Used e.g. to transition from THINKING → SPEAKING without a visual flash.
     */
    fun update(state: AIOSIslandState) {
        val current = _displayState.value
        if (current.isVisible && getPriority(state) >= getPriority(current.state)) {
            AppLogger.d(TAG, "DynamicIsland update: ${current.state} → $state")
            _displayState.value = current.copy(state = state)
        }
    }

    /**
     * Dismisses the island (hides it).
     */
    fun dismiss() {
        cancelAutoDismiss()
        val current = _displayState.value
        if (current.isVisible) {
            AppLogger.d(TAG, "DynamicIsland: ${current.state} → IDLE")
        }
        _displayState.value = AIOSIslandDisplayState() // back to default hidden state
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Determines if a given state should be shown based on BuddyMode policy.
     *
     * ACTIVE  → show all states
     * QUIET   → show task/reminder/memory confirmations; suppress ambient THINKING/SPEAKING
     * SILENT  → show only TASK_CREATED / REMINDER (user-requested); suppress voice states
     * OFF     → show nothing
     */
    private fun shouldShow(state: AIOSIslandState, buddyMode: BuddyMode): Boolean {
        return when (buddyMode) {
            BuddyMode.ACTIVE -> true
            BuddyMode.QUIET  -> state != AIOSIslandState.IDLE
            BuddyMode.SILENT -> state == AIOSIslandState.TASK_CREATED ||
                                state == AIOSIslandState.REMINDER ||
                                state == AIOSIslandState.MEMORY_SAVED ||
                                state == AIOSIslandState.ERROR
            BuddyMode.OFF    -> false
        }
    }

    private fun scheduleAutoDismiss(delayMs: Long) {
        autoDismissJob = scope.launch {
            delay(delayMs)
            dismiss()
        }
    }

    private fun cancelAutoDismiss() {
        autoDismissJob?.cancel()
        autoDismissJob = null
    }
}
