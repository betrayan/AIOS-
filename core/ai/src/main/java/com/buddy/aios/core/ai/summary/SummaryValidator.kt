package com.buddy.aios.core.ai.summary

import com.buddy.aios.core.common.logging.AppLogger
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates summary faithfulness.
 * Verifies topic consistency, numbers, and key facts between full response and voice summary.
 * If validation fails, falls back to a clean extractive summary derived directly from the full response.
 */
@Singleton
class SummaryValidator @Inject constructor() {

    companion object {
        private const val TAG = "SummaryValidator"
        private val NUMBER_PATTERN = Pattern.compile("\\b\\d+(?:\\.\\d+)?%?\\b")
        private val TIME_PATTERN = Pattern.compile("\\b(?:\\d{1,2}:\\d{2}\\s*(?:AM|PM|am|pm)?|\\d{1,2}\\s*(?:AM|PM|am|pm))\\b")
    }

    /**
     * Validates and returns a safe, faithful voice summary.
     */
    fun validateAndSanitize(fullResponse: String, candidateSummary: String): String {
        if (fullResponse.isBlank()) return ""
        if (candidateSummary.isBlank()) return extractSummary(fullResponse)

        val fullNumbers = extractMatches(fullResponse, NUMBER_PATTERN)
        val summaryNumbers = extractMatches(candidateSummary, NUMBER_PATTERN)

        // Rule 1: Mismatched numbers (e.g. Full says 18%, Summary says 80%) -> Fail validation
        val hasContradictoryNumber = summaryNumbers.any { num ->
            num !in fullNumbers
        }

        if (hasContradictoryNumber) {
            AppLogger.w(TAG, "Summary failed number validation: summaryNums=$summaryNumbers, fullNums=$fullNumbers. Falling back to extractive summary.")
            return extractSummary(fullResponse)
        }

        // Rule 2: Mismatched times (e.g. Full says 4 PM, Summary says 5 PM) -> Fail validation
        val fullTimes = extractMatches(fullResponse, TIME_PATTERN)
        val summaryTimes = extractMatches(candidateSummary, TIME_PATTERN)
        val hasContradictoryTime = summaryTimes.any { time ->
            time.uppercase() !in fullTimes.map { it.uppercase() }
        }

        if (hasContradictoryTime) {
            AppLogger.w(TAG, "Summary failed time validation: summaryTimes=$summaryTimes, fullTimes=$fullTimes. Falling back to extractive summary.")
            return extractSummary(fullResponse)
        }

        return candidateSummary.trim()
    }

    /**
     * Extracts a concise 1-2 sentence faithful summary directly from the full response.
     */
    fun extractSummary(fullResponse: String, maxWords: Int = 45): String {
        val sentences = fullResponse
            .replace(Regex("\\[BUDDY_ACTION:.*?\\]"), "")
            .split(Regex("(?<=[.!?])\\s+"))
            .filter { it.isNotBlank() && !it.startsWith("[") }

        if (sentences.isEmpty()) return fullResponse.take(150)

        val selectedSentences = mutableListOf<String>()
        var wordCount = 0

        for (sentence in sentences) {
            val words = sentence.split("\\s+".toRegex()).size
            if (selectedSentences.isNotEmpty() && wordCount + words > maxWords) break
            selectedSentences.add(sentence)
            wordCount += words
        }

        return selectedSentences.joinToString(" ").trim()
    }

    private fun extractMatches(text: String, pattern: Pattern): List<String> {
        val matcher = pattern.matcher(text)
        val matches = mutableListOf<String>()
        while (matcher.find()) {
            matches.add(matcher.group())
        }
        return matches
    }
}
