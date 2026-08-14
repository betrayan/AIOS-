package com.buddy.aios.core.domain.agent

/**
 * Controlled high-level state of the AIOS Agent Brain.
 * Used by UI, Dynamic Island, and BuddyOrb visual components.
 */
enum class AgentStatus {
    IDLE,
    UNDERSTANDING,
    PLANNING,
    WAITING_CONFIRMATION,
    EXECUTING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
}
