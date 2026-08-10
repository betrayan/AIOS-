package com.buddy.aios.core.ai.memory

import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.result.Result
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryExtractor @Inject constructor(
    private val memoryRepository: IMemoryRepository,
) {
    companion object {
        private const val TAG = "MemoryExtractor"
        private const val MIN_CONTENT_LENGTH = 10

        private val MEMORABLE_PATTERNS = listOf(
            Regex("""I prefer\b""", RegexOption.IGNORE_CASE),
            Regex("""I like\b""", RegexOption.IGNORE_CASE),
            Regex("""I love\b""", RegexOption.IGNORE_CASE),
            Regex("""I hate\b""", RegexOption.IGNORE_CASE),
            Regex("""I always\b""", RegexOption.IGNORE_CASE),
            Regex("""I usually\b""", RegexOption.IGNORE_CASE),
            Regex("""I tend to\b""", RegexOption.IGNORE_CASE),
            Regex("""remember that\b""", RegexOption.IGNORE_CASE),
            Regex("""please remember\b""", RegexOption.IGNORE_CASE),
            Regex("""don.t forget that\b""", RegexOption.IGNORE_CASE),
            Regex("""my goal is\b""", RegexOption.IGNORE_CASE),
            Regex("""my plan is\b""", RegexOption.IGNORE_CASE),
            Regex("""I.m working on\b""", RegexOption.IGNORE_CASE),
            Regex("""I study\b""", RegexOption.IGNORE_CASE),
            Regex("""I work as\b""", RegexOption.IGNORE_CASE),
            Regex("""I.m a\b""", RegexOption.IGNORE_CASE),
        )
    }

    suspend fun maybeExtract(
        userMessage: String,
        buddyMode: BuddyMode,
        privacyLevel: PrivacyLevel,
        conversationId: String,
    ) {
        if (buddyMode == BuddyMode.OFF) {
            AppLogger.d(TAG, "Skipping extraction: BuddyMode=OFF")
            return
        }

        if (userMessage.length < MIN_CONTENT_LENGTH) return

        if (!isMemoryWorthy(userMessage)) return

        val importanceScore = computeImportance(userMessage)
        val now = System.currentTimeMillis()
        val memory = Memory(
            id = UUID.randomUUID().toString(),
            userId = "local",
            summary = userMessage.take(300),
            sourceConversationId = conversationId,
            importance = importanceScore,
            createdAt = now,
            lastAccessedAt = now,
            expiresAt = null,
        )

        when (val result = memoryRepository.saveMemory(memory)) {
            is Result.Success -> AppLogger.d(TAG, "Memory extracted: '${memory.summary.take(60)}…'")
            is Result.Error -> AppLogger.w(TAG, "Memory extraction failed: ${result.error}")
        }
    }

    fun isMemoryWorthy(message: String): Boolean {
        return MEMORABLE_PATTERNS.any { it.containsMatchIn(message) }
    }

    private fun computeImportance(message: String): Float {
        val strongSignals = listOf("remember", "goal", "plan", "prefer", "important")
        val hasStrongSignal = strongSignals.any { message.contains(it, ignoreCase = true) }
        return if (hasStrongSignal) 0.8f else 0.6f
    }
}
