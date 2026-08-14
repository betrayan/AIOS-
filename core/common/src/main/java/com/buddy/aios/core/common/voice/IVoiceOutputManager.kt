package com.buddy.aios.core.common.voice

/**
 * Abstraction for voice/TTS output used by lower-level modules (workers, receivers).
 *
 * This interface lives in core:common so that workers and other non-feature modules
 * can depend on it without creating upward dependencies on feature:chat.
 *
 * The concrete implementation (TextToSpeechManager) is provided via Hilt in the
 * app module, which has visibility over both core:common and feature:chat.
 */
interface IVoiceOutputManager {
    /**
     * Speak the given [text] aloud if voice output is currently permitted
     * (i.e. BuddyMode allows voice and TTS is initialised).
     */
    fun speak(text: String)

    /**
     * Stop any currently active speech immediately.
     */
    fun stop()
}
