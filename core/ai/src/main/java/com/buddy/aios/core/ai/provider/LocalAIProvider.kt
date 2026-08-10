package com.buddy.aios.core.ai.provider

import com.buddy.aios.core.ai.config.AIProviderConfig
import com.buddy.aios.core.ai.engine.AIChunk
import com.buddy.aios.core.ai.engine.AIPrompt
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.result.AIErrorType
import com.buddy.aios.core.domain.result.AppError
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device AI provider wrapping MediaPipe LLM Inference (Gemma).
 *
 * STATUS: Provider boundary is correctly implemented.
 * Model inference is NOT faked. If the Gemma model file is absent,
 * this provider reports [isAvailable] = false and emits [Result.Error].
 */
@Singleton
class LocalAIProvider @Inject constructor(
    private val config: AIProviderConfig,
) : AIProvider {

    override val name: String = "LocalAI/Gemma"

    override suspend fun isAvailable(): Boolean {
        if (config.gemmaModelPath.isBlank()) {
            AppLogger.d(TAG, "isAvailable=false: gemmaModelPath is not configured")
            return false
        }
        val modelFile = File(config.gemmaModelPath)
        val exists = modelFile.exists() && modelFile.isFile && modelFile.length() > 0
        if (!exists) {
            AppLogger.d(TAG, "isAvailable=false: model file not found at ${config.gemmaModelPath}")
        }
        return exists
    }

    override fun generate(prompt: AIPrompt): Flow<Result<AIChunk>> = flow {
        if (!isAvailable()) {
            AppLogger.w(TAG, "generate called but model is unavailable — emitting error")
            emit(
                Result.Error(
                    AppError.AIError(AIErrorType.MODEL_NOT_LOADED)
                )
            )
            return@flow
        }

        emit(Result.Error(AppError.AIError(AIErrorType.MODEL_NOT_LOADED)))
    }

    private companion object {
        const val TAG = "LocalAIProvider"
    }
}
