package com.buddy.aios.core.domain.entity

/**
 * State machine representing daily Morning Wish interactive alarm status.
 * State identity is reset per local calendar date (morning_wish_YYYY_MM_DD).
 */
enum class MorningWishState {
    /** Morning wish has not triggered for today. */
    NOT_TRIGGERED,

    /** Morning wish trigger alarm has fired. */
    TRIGGERED,

    /** Morning wish has spoken and is waiting for voice, volume button, or notification acknowledgement. */
    WAITING_FOR_ACK,

    /** Morning wish was successfully acknowledged by the user for today. Repeats cease for remainder of day. */
    ACKNOWLEDGED,

    /** Morning wish was explicitly dismissed or silenced. */
    DISMISSED,
}
