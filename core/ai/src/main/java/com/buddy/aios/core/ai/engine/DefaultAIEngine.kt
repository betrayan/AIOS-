package com.buddy.aios.core.ai.engine

import android.util.Log
import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.result.AIErrorType
import com.buddy.aios.core.domain.result.AppError
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Emergency fallback engine — used ONLY when [AIOrchestrator] is not bound.
 *
 * IMPORTANT: This implementation does NOT:
 * - Keyword-match user input
 * - Generate hardcoded responses
 * - Fake AI inference
 *
 * It simply reports that AI is unavailable with a clear, honest message.
 * This class should never be active in normal operation.
 *
 * In production: [AIOrchestrator] is bound as [AIEngine] via [AIModule].
 */
class DefaultAIEngine @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : AIEngine {

    companion object {
        private const val TAG = "DefaultAIEngine"
    }

    override fun complete(prompt: AIPrompt): Flow<Result<AIChunk>> = flow {
        Log.e(TAG, "DefaultAIEngine.complete() called — AIOrchestrator was not bound correctly.")
        emit(Result.Error(AppError.AIError(AIErrorType.INFERENCE_FAILED)))
    }.flowOn(dispatchers.default)

    override suspend fun summarize(messages: List<Message>): Result<String> {
        return Result.Error(AppError.AIError(AIErrorType.INFERENCE_FAILED))
    }

    override suspend fun isAvailable(): Boolean = false

    override suspend fun release() { /* no-op */ }
}
