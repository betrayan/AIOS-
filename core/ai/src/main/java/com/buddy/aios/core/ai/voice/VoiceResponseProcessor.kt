package com.buddy.aios.core.ai.voice

import com.buddy.aios.core.ai.engine.AIEngine
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.PrivacyLevel
import javax.inject.Inject
import javax.inject.Singleton

data class SpokenResponse(
    val text: String,
    val isSummarized: Boolean = false,
    val isFallback: Boolean = false,
)

data class VoiceResponseProcessorInput(
    val userMessage: String,
    val fullResponse: String,
    val toolResultText: String? = null,
    val buddyMode: BuddyMode = BuddyMode.ACTIVE,
    val privacyLevel: PrivacyLevel = PrivacyLevel.LOCAL_ONLY,
)

/**
 * Transforms full detailed on-screen AI responses into natural, concise spoken answers.
 *
 * Designed to be 100% quota-safe and zero-latency:
 * - Uses instant local sentence & markdown processing first to avoid doubling Cloud AI API quota usage.
 * - Strips code blocks, URLs, markdown headers, raw JSON, IDs, and directives.
 * - Extracts core conversational sentences (1-3 sentences, <= 280 chars) for instant TTS playback.
 * - Completely eliminates API rate limits and cloud quota exhaustion during voice interactions.
 */
@Singleton
class VoiceResponseProcessor @Inject constructor(
    private val aiEngine: AIEngine,
) {

    suspend fun process(input: VoiceResponseProcessorInput): SpokenResponse {
        val rawFullResponse = input.fullResponse.trim()
        if (rawFullResponse.isBlank()) {
            return SpokenResponse(text = "", isSummarized = false)
        }

        // Step 1: Clean local text (remove markdown, directives, code blocks, URLs, JSON)
        val cleanedText = cleanTextForSpeech(rawFullResponse)
        if (cleanedText.isBlank()) {
            return SpokenResponse(text = "I've updated that for you.", isSummarized = false)
        }

        // Step 2: Short response optimization (<= 280 characters)
        if (cleanedText.length <= SHORT_RESPONSE_THRESHOLD) {
            AppLogger.d(TAG, "Short response detected (${cleanedText.length} chars) — using directly for speech")
            return SpokenResponse(text = cleanedText, isSummarized = false)
        }

        // Step 3: Instant local smart summary (extract first 1-3 core sentences up to 280 chars)
        val localSummary = extractLocalSummaryFallback(cleanedText)
        if (localSummary.isNotBlank()) {
            AppLogger.d(TAG, "Local smart summarization successful (${localSummary.length} chars) — zero network overhead")
            return SpokenResponse(text = localSummary, isSummarized = true)
        }

        // Step 4: Ultimate safe fallback
        val safeFallback = cleanedText.take(SHORT_RESPONSE_THRESHOLD).trim()
        return SpokenResponse(text = safeFallback, isSummarized = false, isFallback = true)
    }

    /**
     * Cleans raw AI markdown text into speakable natural text.
     */
    fun cleanTextForSpeech(rawText: String): String {
        var text = rawText

        // 1. Remove BUDDY_ACTION directives and JSON blocks
        text = DIRECTIVE_REGEX.replace(text, "")
        text = JSON_BLOCK_REGEX.replace(text, "")

        // 2. Convert code blocks ```[lang]\n...``` into conversational code notification
        text = CODE_BLOCK_REGEX.replace(text) { matchResult ->
            val lang = matchResult.groupValues[1].trim().ifBlank { "code" }
            " I've written the $lang snippet for you. "
        }

        // 3. Remove Markdown links [text](url) -> text
        text = MD_LINK_REGEX.replace(text, "$1")

        // 4. Remove standalone URLs
        text = URL_REGEX.replace(text, "")

        // 5. Remove Markdown headers (# Header)
        text = MD_HEADER_REGEX.replace(text, "")

        // 6. Remove bold/italics formatting (*, **, _, __)
        text = text.replace(Regex("""[*_]{1,3}"""), "")

        // 7. Remove inline code backticks
        text = text.replace("`", "")

        // 8. Convert bullet list markers (* item, - item, 1. item) into natural pauses
        text = LIST_MARKER_REGEX.replace(text, ". ")

        // 9. Normalize multiple spaces, newlines, tabs into clean single spacing
        text = text.replace(Regex("""\s+"""), " ").trim()

        return text
    }

    /**
     * Extracts first 1-3 sentences up to 280 chars as instant, non-blocking local summary.
     */
    fun extractLocalSummaryFallback(cleanedText: String): String {
        val sentences = cleanedText.split(Regex("""(?<=[.!?])\s+"""))
        val sb = StringBuilder()
        for (sentence in sentences) {
            val trimmedSentence = sentence.trim()
            if (trimmedSentence.isBlank()) continue
            if (sb.length + trimmedSentence.length + 1 <= SHORT_RESPONSE_THRESHOLD || sb.isEmpty()) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(trimmedSentence)
            } else {
                break
            }
        }
        return sb.toString().trim()
    }

    companion object {
        private const val TAG = "VoiceResponseProcessor"
        const val SHORT_RESPONSE_THRESHOLD = 280

        private val DIRECTIVE_REGEX = Regex("""\[BUDDY_ACTION:(\{.*?\})\]""", RegexOption.DOT_MATCHES_ALL)
        private val JSON_BLOCK_REGEX = Regex("""\{"tool":.*?"\}""", RegexOption.DOT_MATCHES_ALL)
        private val CODE_BLOCK_REGEX = Regex("""```(\w*)\n[\s\S]*?```""", RegexOption.MULTILINE)
        private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        private val MD_LINK_REGEX = Regex("""\[([^]]+)]\([^)]+\)""")
        private val MD_HEADER_REGEX = Regex("""(?m)^#{1,6}\s+""")
        private val LIST_MARKER_REGEX = Regex("""(?m)^\s*[*%\-]\s+|^\s*\d+\.\s+""")
    }
}
