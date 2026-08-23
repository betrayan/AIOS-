package com.buddy.aios.core.ai.voice

import com.buddy.aios.core.ai.engine.AIChunk
import com.buddy.aios.core.ai.engine.AIEngine
import com.buddy.aios.core.ai.engine.AIPrompt
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.result.Result
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

enum class ResponseType {
    SHORT_CONVERSATION,
    TOOL_CONFIRMATION,
    JARVIS_EXPLANATION,
    ERROR,
}

/**
 * AIOS JARVIS / FRIDAY Companion Voice Explanation Engine.
 *
 * Designed from scratch to behave like Marvel's JARVIS or FRIDAY.
 *
 * Design Principles:
 * - NEVER reads the screen text word-for-word like a robotic screen reader.
 * - NEVER reads raw code syntax (`public class...`), URLs, JSON, directives, or markdown symbols out loud.
 * - Acts as an intelligent companion sitting beside the user, explaining the core concept, logic, or answer in natural spoken English (30–60 words).
 * - For code requests: Explains what the code does or provides a clear conceptual summary, noting that the solution is on screen.
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

        // 1. Tool Confirmation / Action Status
        if (input.toolResultText != null || DIRECTIVE_REGEX.containsMatchIn(rawFullResponse) || JSON_BLOCK_REGEX.containsMatchIn(rawFullResponse)) {
            val cleanConfirmation = formatToolConfirmation(rawFullResponse, input.toolResultText)
            return SpokenResponse(text = cleanConfirmation, isSummarized = false)
        }

        // 2. Short Conversational Greeting / Chitchat (under 120 chars)
        val sanitizedText = sanitizeForSpeech(rawFullResponse)
        if (sanitizedText.length <= SHORT_CONVERSATION_THRESHOLD && !CODE_BLOCK_REGEX.containsMatchIn(rawFullResponse)) {
            return SpokenResponse(text = sanitizedText, isSummarized = false)
        }

        // 3. JARVIS / FRIDAY Companion Explanation
        // Try generating a natural companion explanation using the LLM
        val jarvisExplanation = tryGenerateJarvisExplanation(input, rawFullResponse)
        if (!jarvisExplanation.isNullOrBlank()) {
            val sanitized = sanitizeForSpeech(jarvisExplanation)
            if (sanitized.isNotBlank()) {
                return SpokenResponse(text = sanitized, isSummarized = true)
            }
        }

        // 4. Intelligent Rule-Based Fallback (Instant, faithful, companion-style explanation)
        val fallbackExplanation = generateRuleBasedJarvisExplanation(input.userMessage, rawFullResponse)
        return SpokenResponse(text = fallbackExplanation, isSummarized = true, isFallback = true)
    }

    /**
     * Generates a JARVIS/FRIDAY style spoken explanation using AI.
     */
    private suspend fun tryGenerateJarvisExplanation(
        input: VoiceResponseProcessorInput,
        fullResponse: String,
    ): String? {
        return withTimeoutOrNull(JARVIS_EXPLANATION_TIMEOUT_MS) {
            try {
                val systemInstruction = JARVIS_VOICE_SYSTEM_PROMPT
                    .replace("{USER_MESSAGE}", input.userMessage)
                    .replace("{FULL_AI_RESPONSE}", fullResponse.take(2000))

                val prompt = AIPrompt(
                    userMessage = input.userMessage,
                    systemInstruction = systemInstruction,
                    conversationHistory = emptyList(),
                    temperature = 0.4f,
                    maxOutputTokens = 120,
                )

                var resultText = ""
                aiEngine.complete(prompt).collect { chunkResult ->
                    if (chunkResult is Result.Success) {
                        resultText += chunkResult.value.text
                    }
                }
                resultText.trim()
            } catch (e: Exception) {
                AppLogger.w(TAG, "JARVIS voice generation exception: ${e.message}")
                null
            }
        }
    }

    /**
     * Fallback JARVIS explanation engine when offline or timing out.
     * Takes key conceptual explanation without reading code or markdown headers.
     */
    private fun generateRuleBasedJarvisExplanation(userMessage: String, fullResponse: String): String {
        val hasCode = CODE_BLOCK_REGEX.containsMatchIn(fullResponse)
        val codeFreeText = sanitizeForSpeech(fullResponse.replace(CODE_BLOCK_REGEX, ""))

        if (hasCode) {
            val match = CODE_BLOCK_REGEX.find(fullResponse)
            val lang = match?.groupValues?.get(1)?.trim()?.ifBlank { "code" } ?: "code"

            return if (codeFreeText.length >= 40) {
                val conceptSummary = extractKeySentences(codeFreeText, maxSentences = 2)
                "$conceptSummary I've placed the $lang solution on screen for you."
            } else {
                "I've written the $lang code solution for you on screen."
            }
        }

        if (codeFreeText.isNotBlank()) {
            return extractKeySentences(codeFreeText, maxSentences = 3)
        }

        return "Here is what I found for you."
    }

    private fun extractKeySentences(text: String, maxSentences: Int = 2): String {
        val sentences = text.split(Regex("""(?<=[.!?])\s+""")).filter { it.isNotBlank() }
        if (sentences.isEmpty()) return text.take(180)
        return sentences.take(maxSentences).joinToString(" ").trim()
    }

    private fun formatToolConfirmation(rawResponse: String, toolResultText: String?): String {
        val text = sanitizeForSpeech(rawResponse)
        if (text.isNotBlank() && text.length <= 140) {
            return text
        }
        return toolResultText ?: "All done! I've updated that for you."
    }

    /**
     * Legacy method maintained for binary compatibility with existing tests.
     */
    fun extractFullContextLocalExplanation(rawResponse: String): String {
        val cleaned = cleanTextForSpeech(rawResponse)
        val sentences = cleaned.split(Regex("""(?<=[.!?])\s+""")).filter { it.isNotBlank() }

        if (sentences.size <= 3) {
            return sentences.joinToString(" ")
        }

        val firstSentence = sentences.first()
        val middleSentence = sentences[sentences.size / 2]
        val lastSentence = sentences.last()

        val fullSummary = "$firstSentence $middleSentence $lastSentence"
        return if (fullSummary.length <= 350) fullSummary else "$firstSentence $middleSentence"
    }

    /**
     * Legacy method maintained for binary compatibility with existing tests.
     */
    fun cleanTextForSpeech(rawText: String): String {
        return sanitizeForSpeech(rawText)
    }

    /**
     * Sanitizes raw markdown / technical text into natural, speakable English.
     */
    fun sanitizeForSpeech(rawText: String): String {
        var text = rawText

        // 1. Remove BUDDY_ACTION directives and JSON blocks
        text = DIRECTIVE_REGEX.replace(text, "")
        text = JSON_BLOCK_REGEX.replace(text, "")

        // 2. Remove Markdown links [text](url) -> text
        text = MD_LINK_REGEX.replace(text, "$1")

        // 3. Remove standalone URLs
        text = URL_REGEX.replace(text, "")

        // 4. Remove Markdown headers (# Header)
        text = MD_HEADER_REGEX.replace(text, "")

        // 5. Remove bold/italics formatting (*, **, _, __)
        text = text.replace(Regex("""[*_]{1,3}"""), "")

        // 6. Remove inline code backticks
        text = text.replace("`", "")

        // 7. Convert bullet list markers into natural sentence breaks
        text = LIST_MARKER_REGEX.replace(text, ". ")

        // 8. Normalize multiple spaces, newlines, tabs into clean single spacing
        text = text.replace(Regex("""\s+"""), " ").trim()

        return text
    }

    companion object {
        private const val TAG = "VoiceResponseProcessor"
        private const val SHORT_CONVERSATION_THRESHOLD = 120
        private const val JARVIS_EXPLANATION_TIMEOUT_MS = 4500L

        private val DIRECTIVE_REGEX = Regex("""\[BUDDY_ACTION:(\{.*?\})\]""", RegexOption.DOT_MATCHES_ALL)
        private val JSON_BLOCK_REGEX = Regex("""\{"tool":.*?"\}""", RegexOption.DOT_MATCHES_ALL)
        private val CODE_BLOCK_REGEX = Regex("""```(\w*)\n[\s\S]*?```""", RegexOption.MULTILINE)
        private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        private val MD_LINK_REGEX = Regex("""\[([^]]+)]\([^)]+\)""")
        private val MD_HEADER_REGEX = Regex("""(?m)^#{1,6}\s+""")
        private val LIST_MARKER_REGEX = Regex("""(?m)^\s*[*%\-]\s+|^\s*\d+\.\s+""")

        val JARVIS_VOICE_SYSTEM_PROMPT = """
            You are Buddy, an advanced personal AI companion inspired by JARVIS and FRIDAY from Marvel.
            The user is speaking with you naturally via voice.
            The screen shows the full detailed response.

            User asked: "{USER_MESSAGE}"
            Full screen response: "{FULL_AI_RESPONSE}"

            Task:
            Explain the core concept, answer, or technical solution to the user in natural spoken English.

            Rules:
            1. DO NOT read the screen text word-for-word.
            2. Speak like JARVIS or FRIDAY — intelligent, concise, warm, loyal, and clear.
            3. Deliver the spoken explanation in 2 to 3 clear sentences (30 to 55 words max).
            4. Never read raw code, syntax (`public class`), JSON, URLs, headers, or markdown symbols out loud.
            5. If code was generated, explain what the concept or code accomplishes in plain, intelligent English.
            6. Never say "on screen", "as summarized above", or "here is a summary". Speak directly and naturally.

            Return ONLY the spoken explanation.
        """.trimIndent()
    }
}
