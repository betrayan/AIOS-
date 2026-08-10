package com.buddy.aios.feature.chat.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import com.buddy.aios.core.common.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safe Android [TextToSpeech] engine wrapper.
 */
@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to instantiate TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            isInitialized = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            AppLogger.d(TAG, "TextToSpeech initialized successfully (isInitialized=$isInitialized)")
        } else {
            AppLogger.w(TAG, "TextToSpeech initialization failed with status: $status")
            isInitialized = false
        }
    }

    fun speak(text: String) {
        if (!isInitialized || text.isBlank()) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "buddy_tts_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error speaking text", e)
        }
    }

    fun stop() {
        try {
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error stopping TextToSpeech: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error shutting down TextToSpeech: ${e.message}")
        } finally {
            tts = null
            isInitialized = false
        }
    }

    private companion object {
        const val TAG = "TextToSpeechManager"
    }
}
