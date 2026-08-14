package com.buddy.aios.core.domain.repository

import com.buddy.aios.core.domain.entity.MorningWishState

/**
 * Domain interface for Morning Wish Interactive Alarm orchestration.
 */
interface IMorningWishEngine {
    /** Schedules the next exact morning wish alarm in local device timezone. */
    suspend fun scheduleMorningWish()

    /** Triggers the Morning Wish flow (first time, manual voice command, or 10-minute repeat). */
    suspend fun triggerMorningWish(isManualTrigger: Boolean = false, isRepeat: Boolean = false, repeatCount: Int = 0)

    /** Acknowledges or silences the current Morning Wish session. */
    suspend fun acknowledgeMorningWish(source: String = "voice")

    /** Returns current MorningWishState for today's local date. */
    suspend fun getTodayState(): MorningWishState

    /** Returns true if Morning Wish session is currently active and waiting for acknowledgement. */
    fun isWaitingForAcknowledgement(): Boolean
}
