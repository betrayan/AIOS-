package com.buddy.aios.core.ai.engine

import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Core abstraction for all AI inference operations.
 *
 * The single implementation in production is [AIOrchestrator], which routes
 * through [AIPolicy] → [ContextManager] → [AIProvider].
 *
 * All methods are streaming-capable via Flow for real-time token output.
 */
interface AIEngine {

    /**
     * Completes a prompt given a list of messages as context.
     * Emits partial text chunks as they arrive (streaming), then completes.
     */
    fun complete(prompt: AIPrompt): Flow<Result<AIChunk>>

    /**
     * Summarises a list of messages into a compact summary string.
     * Used by [ContextManager] when the context window is near capacity.
     */
    suspend fun summarize(messages: List<Message>): Result<String>

    /** Returns true if this engine is currently available. */
    suspend fun isAvailable(): Boolean

    /** Release resources (model weights from memory). */
    suspend fun release()
}

/** The input passed to an AI engine for completion. */
data class AIPrompt(
    val systemInstruction: String,
    val conversationHistory: List<Message>,
    val userMessage: String,
    /** Used by AIOrchestrator to retrieve conversation-specific context (memories, etc.) */
    val conversationId: String = "",
    val maxOutputTokens: Int = 2048,
    val temperature: Float = 0.7f,
)

/** A single streamed chunk of AI output (partial token or full response). */
data class AIChunk(
    val text: String,
    val isComplete: Boolean,
    val totalTokensUsed: Int = 0,
    /** Populated only on the final complete chunk — the tool that was executed, if any. */
    val toolExecuted: com.buddy.aios.core.ai.tool.BuddyTool? = null,
    /** Populated only on the final complete chunk — the result of tool execution, if any. */
    val toolResult: com.buddy.aios.core.ai.tool.ToolResult? = null,
)
