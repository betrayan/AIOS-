package com.buddy.aios.core.domain.repository

import com.buddy.aios.core.domain.entity.BuddyCapability
import com.buddy.aios.core.domain.entity.BuddyMode
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing and observing the centralized Buddy AI OS operating mode.
 */
interface IBuddyModeRepository {

    /** Observe the current [BuddyMode] state. */
    fun observeBuddyMode(): Flow<BuddyMode>

    /** Observe the current [BuddyCapability] set based on the active mode. */
    fun observeCapabilities(): Flow<BuddyCapability>

    /** Get the instantaneous current [BuddyMode]. */
    suspend fun getBuddyMode(): BuddyMode

    /** Get the instantaneous current [BuddyCapability]. */
    suspend fun getCapabilities(): BuddyCapability

    /** Set/update the system-wide [BuddyMode]. */
    suspend fun setBuddyMode(mode: BuddyMode)
}
