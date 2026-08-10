package com.buddy.aios.core.ai.provider

import com.buddy.aios.core.ai.engine.AIChunk
import com.buddy.aios.core.ai.engine.AIPrompt
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction boundary for AI inference providers.
 *
 * The rest of the application never depends on provider-specific classes.
 * All provider selection logic lives in [AIOrchestrator].
 *
 * Implementations:
 * - [LocalAIProvider]  — MediaPipe Gemma on-device inference
 * - [CloudAIProvider]  — Gemini REST API (cloud, opt-in only)
 */
interface AIProvider {

    /** Unique name for logging and diagnostics. */
    val name: String

    /**
     * Returns true if this provider can currently serve requests.
     * - [LocalAIProvider]: checks if the model file is present and loaded.
     * - [CloudAIProvider]: checks if an API key is configured and network is reachable.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Generates a response to [prompt], streaming token chunks as they arrive.
     * The final chunk has [AIChunk.isComplete] == true.
     */
    fun generate(prompt: AIPrompt): Flow<Result<AIChunk>>
}
