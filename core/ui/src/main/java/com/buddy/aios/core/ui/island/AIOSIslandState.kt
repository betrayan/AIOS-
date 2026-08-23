package com.buddy.aios.core.ui.island

/**
 * Represents the possible visual states of the AIOS Dynamic Island overlay.
 *
 * Each state maps to a distinct pill appearance, color, and animation behavior.
 * This enum is the single source of truth for island state — it is consumed by
 * both the [AIOSIslandStateManager] and the [AIOSIsland] composable.
 */
enum class AIOSIslandState {
    /** Orb is idle — no active AIOS event. Island is hidden. */
    IDLE,

    /** AIOS is actively listening to voice input. */
    LISTENING,

    /** AIOS is processing a response. */
    THINKING,

    /** AIOS is speaking a response via TTS. */
    SPEAKING,

    /** AIOS is executing a tool (creating reminder, setting alarm, etc.). */
    TOOL_EXECUTION,

    /** A task or reminder was successfully created. */
    TASK_CREATED,

    /** A time-sensitive reminder is firing. */
    REMINDER,

    /** Morning Wish briefing is active. */
    MORNING_WISH,

    /** Multi-turn continuous conversation mode is active. */
    CONTINUOUS,

    /** Continuous conversation mode is stopping. */
    STOPPING,

    /** A memory was saved to long-term memory. */
    MEMORY_SAVED,

    /** A general AIOS informational message. */
    AIOS_MESSAGE,

    /** An error occurred during an AIOS operation. */
    ERROR,
}

/**
 * The complete display data for one Dynamic Island presentation.
 *
 * @param state          Which visual state to show.
 * @param message        Short text displayed in the compact pill (max ~30 chars).
 * @param isVisible      Whether the island is currently shown.
 * @param autoDismissMs  If > 0, automatically dismisses after this many milliseconds.
 *                       Use 0 for states that persist until explicitly dismissed (e.g. THINKING).
 * @param actionLabel    Optional label for the expanded action button (e.g. "Open Chat").
 * @param onAction       Invoked when the action button is tapped.
 */
data class AIOSIslandDisplayState(
    val state: AIOSIslandState = AIOSIslandState.IDLE,
    val message: String = "",
    val isVisible: Boolean = false,
    val autoDismissMs: Long = 0L,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)
