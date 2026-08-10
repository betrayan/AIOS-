package com.buddy.aios.core.ai.voice

import com.buddy.aios.core.ai.engine.AIEngine
import com.buddy.aios.core.ai.engine.AIPrompt
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
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
 * Principles:
 * - Does NOT replace on-screen detailed text.
 * - Does NOT run extra AI requests for short responses (<= 250 chars).
 * - Strips code blocks, URLs, markdown, directives, raw JSON, IDs.
 * - Summarizes long text into 1-4 natural conversational sentences.
 * - Provides immediate, non-blocking local fallback on timeout/failure.
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

        // Step 1: Clean local text (remove markdown, directives, code, URLs)
        val cleanedText = cleanTextForSpeech(rawFullResponse)
        if (cleanedText.isBlank()) {
            return SpokenResponse(text = "I've updated that for you.", isSummarized = false)
        }

        // Step 2: Short response optimization (<= 250 characters)
        if (cleanedText.length <= SHORT_RESPONSE_THRESHOLD) {
            AppLogger.d(TAG, "Short response detected (${cleanedText.length} chars) — using directly for speech")
            return SpokenResponse(text = cleanedText, isSummarized = false)
        }

        // Step 3: Attempt AI-based natural conversational summarization with 5-second timeout
        val aiSpokenSummary = tryAiSummarize(input, rawFullResponse)
        if (!aiSpokenSummary.isNullOrBlank()) {
            val cleanedAiSummary = cleanTextForSpeech(aiSpokenSummary)
            if (cleanedAiSummary.isNotBlank()) {
                AppLogger.d(TAG, "AI spoken summarization successful (${cleanedAiSummary.length} chars)")
                return SpokenResponse(text = cleanedAiSummary, isSummarized = true)
            }
        }

        // Step 4: Local fallback (extract first 1-3 sentences up to 250 chars)
        AppLogger.w(TAG, "AI summarization unavailable or timed out — using smart local text extraction fallback")
        val fallbackText = extractLocalSummaryFallback(cleanedText)
        return SpokenResponse(text = fallbackText, isSummarized = false, isFallback = true)
    }

    private suspend fun tryAiSummarize(input: VoiceResponseProcessorInput, fullResponse: String): String? {
        return withTimeoutOrNull(AI_SUMMARIZE_TIMEOUT_MS) {
            try {
                val promptText = VOICE_SUMMARY_SYSTEM_PROMPT
                    .replace("{FULL_AI_RESPONSE}", fullResponse)
                    .replace("{USER_MESSAGE}", input.userMessage)
                    .replace("{TOOL_RESULT}", input.toolResultText ?: "None")

                val prompt = AIPrompt(
                    userMessage = input.userMessage,
                    systemInstruction = promptText,
                    conversationHistory = emptyList(),
                    temperature = 0.5f,
                    maxOutputTokens = 150,
                )

                var summary = ""
                aiEngine.complete(prompt).collect { chunkResult ->
                    if (chunkResult is Result.Success) {
                        summary += chunkResult.value.text
                    }
                }
                summary.trim()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Exception during AI voice summarization: ${e.message}")
                null
            }
        }
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
     * Extracts first 1-3 sentences up to 250 chars as local non-blocking fallback.
     */
    fun extractLocalSummaryFallback(cleanedText: String): String {
        val sentences = cleanedText.split(Regex("""(?<=[.!?])\s+"""))
        val sb = StringBuilder()
        for (sentence in sentences) {
            if (sb.length + sentence.length + 1 <= SHORT_RESPONSE_THRESHOLD || sb.isEmpty()) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(sentence)
            } else {
                break
            }
        }
        return sb.toString().trim()
    }

    companion object {
        private const val TAG = "VoiceResponseProcessor"
        const val SHORT_RESPONSE_THRESHOLD = 250
        const val AI_SUMMARIZE_TIMEOUT_MS = 5000L

        private val DIRECTIVE_REGEX = Regex("""\[BUDDY_ACTION:(\{.*?\})\]""", RegexOption.DOT_MATCHES_ALL)
        private val JSON_BLOCK_REGEX = Regex("""\{"tool":.*?"\}""", RegexOption.DOT_MATCHES_ALL)
        private val CODE_BLOCK_REGEX = Regex("""```(\w*)\n[\s\S]*?```""", RegexOption.MULTILINE)
        private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        private val MD_LINK_REGEX = Regex("""\[([^]]+)]\([^)]+\)""")
        private val MD_HEADER_REGEX = Regex("""(?m)^#{1,6}\s+""")
        private val LIST_MARKER_REGEX = Regex("""(?m)^\s*[*%\-]\s+|^\s*\d+\.\s+""")

        val VOICE_SUMMARY_SYSTEM_PROMPT = """
            You are the spoken-response layer of AIOS, a personal AI companion.
            The user is having a natural conversation with AIOS.
            Your job is NOT to rewrite the entire answer.
            Convert the full AI response into a concise spoken response.

            Rules:
            - Never read the full response word-for-word.
            - Summarize the important information.
            - Directly answer what the user actually asked.
            - Normally use 1–4 sentences.
            - Sound natural and conversational.
            - Speak like a helpful intelligent companion.
            - Do not sound like a document, article, or presentation.
            - Do not mention "the response", "the text", or "the screen".
            - Do not mention headings or sections.
            - Do not read markdown.
            - Do not read bullet points as a list unless the user specifically asks for a list.
            - Do not read code.
            - Do not read URLs.
            - Do not read citations.
            - Preserve important dates, times, numbers, warnings, and conclusions.
            - If an action was completed, clearly confirm it.
            - If something failed, clearly explain what failed and what the user should do.
            - For casual conversation, respond naturally and briefly.
            - If the user explicitly asks for a detailed explanation, provide a slightly longer spoken explanation.
            - Never invent information that is not present in the original response.

            Full AI response:
            {FULL_AI_RESPONSE}

            User's original request:
            {USER_MESSAGE}

            Tool result, if any:
            {TOOL_RESULT}

            Return ONLY the spoken response.
        """.trimIndent()
    }
}
