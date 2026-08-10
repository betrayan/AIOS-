package com.buddy.aios.core.ai.voice

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
    CODE_EXPLANATION,
    LONG_INFORMATION,
    ERROR,
}

/**
 * AIOS Voice Explanation Layer.
 *
 * Responsibility:
 * Understands the COMPLETE AI answer and produces a natural, 40-90 word spoken explanation
 * representing the main idea, key supporting details, and conclusions across the ENTIRE response.
 *
 * Principles:
 * - Does NOT read the first 3 lines or truncate the response.
 * - Does NOT replace on-screen detailed text.
 * - Does NOT read code blocks, raw JSON, URLs, directives, or DB IDs.
 * - Respects user intent (quick vs detailed explanation).
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

        // Detect response type
        val responseType = detectResponseType(rawFullResponse, input.toolResultText)
        AppLogger.d(TAG, "Detected response type: $responseType for msg: ${input.userMessage.take(40)}")

        return when (responseType) {
            ResponseType.SHORT_CONVERSATION -> {
                val cleaned = cleanTextForSpeech(rawFullResponse)
                SpokenResponse(text = cleaned, isSummarized = false)
            }

            ResponseType.TOOL_CONFIRMATION -> {
                val cleanConfirmation = formatToolConfirmation(rawFullResponse, input.toolResultText)
                SpokenResponse(text = cleanConfirmation, isSummarized = false)
            }

            ResponseType.CODE_EXPLANATION -> {
                val spokenExplanation = handleCodeResponse(input, rawFullResponse)
                SpokenResponse(text = spokenExplanation, isSummarized = true)
            }

            ResponseType.LONG_INFORMATION -> {
                // Attempt AI-driven full-response spoken explanation
                val aiSpokenExplanation = tryAiSpokenExplanation(input, rawFullResponse)
                if (!aiSpokenExplanation.isNullOrBlank()) {
                    val cleanedAiExplanation = cleanTextForSpeech(aiSpokenExplanation)
                    if (cleanedAiExplanation.isNotBlank()) {
                        return SpokenResponse(text = cleanedAiExplanation, isSummarized = true)
                    }
                }

                // Fallback: Full-context multi-section local explanation (Start + Middle + Conclusion)
                val fullContextFallback = extractFullContextLocalExplanation(rawFullResponse)
                SpokenResponse(text = fullContextFallback, isSummarized = true, isFallback = true)
            }

            ResponseType.ERROR -> {
                val cleaned = cleanTextForSpeech(rawFullResponse)
                SpokenResponse(text = cleaned, isSummarized = false)
            }
        }
    }

    private fun detectResponseType(fullResponse: String, toolResultText: String?): ResponseType {
        if (toolResultText != null || DIRECTIVE_REGEX.containsMatchIn(fullResponse) || JSON_BLOCK_REGEX.containsMatchIn(fullResponse)) {
            return ResponseType.TOOL_CONFIRMATION
        }

        if (CODE_BLOCK_REGEX.containsMatchIn(fullResponse)) {
            return ResponseType.CODE_EXPLANATION
        }

        val cleaned = cleanTextForSpeech(fullResponse)
        if (cleaned.length <= SHORT_RESPONSE_THRESHOLD) {
            return ResponseType.SHORT_CONVERSATION
        }

        return ResponseType.LONG_INFORMATION
    }

    private suspend fun tryAiSpokenExplanation(input: VoiceResponseProcessorInput, fullResponse: String): String? {
        return withTimeoutOrNull(AI_EXPLANATION_TIMEOUT_MS) {
            try {
                val promptText = VOICE_EXPLANATION_SYSTEM_PROMPT
                    .replace("{FULL_AI_RESPONSE}", fullResponse)
                    .replace("{USER_MESSAGE}", input.userMessage)
                    .replace("{TOOL_RESULT}", input.toolResultText ?: "None")

                val prompt = AIPrompt(
                    userMessage = input.userMessage,
                    systemInstruction = promptText,
                    conversationHistory = emptyList(),
                    temperature = 0.5f,
                    maxOutputTokens = 200,
                )

                var summary = ""
                aiEngine.complete(prompt).collect { chunkResult ->
                    if (chunkResult is Result.Success) {
                        summary += chunkResult.value.text
                    }
                }
                summary.trim()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Exception during AI voice explanation generation: ${e.message}")
                null
            }
        }
    }

    private fun formatToolConfirmation(rawResponse: String, toolResultText: String?): String {
        val text = cleanTextForSpeech(rawResponse)
        if (text.isNotBlank() && text.length <= 150) {
            return text
        }
        return "Done. I've updated that for you."
    }

    private suspend fun handleCodeResponse(input: VoiceResponseProcessorInput, fullResponse: String): String {
        val userQueryLower = input.userMessage.lowercase()
        if (userQueryLower.contains("explain the code") || userQueryLower.contains("explain code")) {
            val aiExplanation = tryAiSpokenExplanation(input, fullResponse)
            if (!aiExplanation.isNullOrBlank()) {
                return cleanTextForSpeech(aiExplanation)
            }
        }

        val match = CODE_BLOCK_REGEX.find(fullResponse)
        val lang = match?.groupValues?.get(1)?.trim()?.ifBlank { "code" } ?: "code"
        val codeFreeText = cleanTextForSpeech(fullResponse.replace(CODE_BLOCK_REGEX, ""))

        return if (codeFreeText.isNotBlank()) {
            "I've written the $lang solution for you on screen. ${codeFreeText.take(150)}"
        } else {
            "I've written the $lang solution for you on screen."
        }
    }

    /**
     * Extracts full-context representation across the start, middle, and conclusion of the response.
     * Guaranteed to NOT just read the first 2 lines.
     */
    fun extractFullContextLocalExplanation(rawResponse: String): String {
        val cleaned = cleanTextForSpeech(rawResponse)
        val sentences = cleaned.split(Regex("""(?<=[.!?])\s+""")).filter { it.isNotBlank() }

        if (sentences.size <= 3) {
            return sentences.joinToString(" ")
        }

        // Select key sentences from start, middle, and end to represent the COMPLETE response
        val firstSentence = sentences.first()
        val middleSentence = sentences[sentences.size / 2]
        val lastSentence = sentences.last()

        val fullSummary = "$firstSentence $middleSentence $lastSentence"
        return if (fullSummary.length <= 350) fullSummary else "$firstSentence $middleSentence"
    }

    /**
     * Cleans raw AI markdown text into speakable natural text.
     */
    fun cleanTextForSpeech(rawText: String): String {
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

        // 7. Convert bullet list markers into natural pauses
        text = LIST_MARKER_REGEX.replace(text, ". ")

        // 8. Normalize multiple spaces, newlines, tabs into clean single spacing
        text = text.replace(Regex("""\s+"""), " ").trim()

        return text
    }

    companion object {
        private const val TAG = "VoiceResponseProcessor"
        const val SHORT_RESPONSE_THRESHOLD = 220
        const val AI_EXPLANATION_TIMEOUT_MS = 6000L

        private val DIRECTIVE_REGEX = Regex("""\[BUDDY_ACTION:(\{.*?\})\]""", RegexOption.DOT_MATCHES_ALL)
        private val JSON_BLOCK_REGEX = Regex("""\{"tool":.*?"\}""", RegexOption.DOT_MATCHES_ALL)
        private val CODE_BLOCK_REGEX = Regex("""```(\w*)\n[\s\S]*?```""", RegexOption.MULTILINE)
        private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        private val MD_LINK_REGEX = Regex("""\[([^]]+)]\([^)]+\)""")
        private val MD_HEADER_REGEX = Regex("""(?m)^#{1,6}\s+""")
        private val LIST_MARKER_REGEX = Regex("""(?m)^\s*[*%\-]\s+|^\s*\d+\.\s+""")

        val VOICE_EXPLANATION_SYSTEM_PROMPT = """
            You are the voice conversation layer of AIOS.
            The user is speaking with AIOS naturally.
            You receive the COMPLETE answer generated for the user's request.
            Your job is to understand the COMPLETE answer and explain its meaning naturally in spoken language.

            IMPORTANT:
            Do NOT read the beginning of the answer.
            Do NOT simply truncate the answer.
            Do NOT copy the first few sentences.
            Do NOT summarize only the first paragraph.

            You MUST consider the entire response before generating the spoken answer.

            Your output should communicate:
            1. The direct answer to the user's question.
            2. The main idea.
            3. The most important supporting information.
            4. The important conclusion or recommendation, if present.

            Simplify technical language when possible.
            Make it sound like a knowledgeable friend explaining something clearly.

            The screen contains the detailed answer.
            Your voice should give the user the understandable version.

            Normally produce approximately 40–90 spoken words.
            For very simple questions, use fewer words.
            For complex questions, use up to approximately 120 words when necessary to preserve the important meaning.

            Never sacrifice the main conclusion just to make the response shorter.
            Never invent information.
            Never mention that you are summarizing.

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
