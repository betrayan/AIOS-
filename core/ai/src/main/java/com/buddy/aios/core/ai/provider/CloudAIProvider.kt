package com.buddy.aios.core.ai.provider

import com.buddy.aios.core.ai.config.AIProviderConfig
import com.buddy.aios.core.ai.engine.AIChunk
import com.buddy.aios.core.ai.engine.AIPrompt
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.MessageRole
import com.buddy.aios.core.domain.result.AIErrorType
import com.buddy.aios.core.domain.result.AppError
import com.buddy.aios.core.domain.result.Result
import com.buddy.aios.core.network.api.GeminiApiService
import com.buddy.aios.core.network.api.GeminiContent
import com.buddy.aios.core.network.api.GeminiGenerationConfig
import com.buddy.aios.core.network.api.GeminiPart
import com.buddy.aios.core.network.api.GeminiRequest
import com.buddy.aios.core.network.api.GeminiSafetySetting
import com.buddy.aios.core.network.api.GeminiSystemInstruction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudAIProvider @Inject constructor(
    private val geminiApi: GeminiApiService,
    private val config: AIProviderConfig,
) : AIProvider {

    override val name: String = "CloudAI/Gemini"

    override suspend fun isAvailable(): Boolean {
        val key = config.geminiApiKey.trim()
        val isConfigured = key.isNotBlank() && key != "null" && key != "\"\""
        AppLogger.d(TAG, "Gemini API key configured = $isConfigured")
        return isConfigured
    }

    override fun generate(prompt: AIPrompt): Flow<Result<AIChunk>> = flow {
        if (!isAvailable()) {
            AppLogger.w(TAG, "[DEBUG_AI_ERROR] AI provider = CLOUD | HTTP status = 0 | Error category = AUTHENTICATION_ERROR | Error message = GEMINI_API_KEY_NOT_CONFIGURED")
            emit(Result.Error(AppError.AIError(AIErrorType.AUTHENTICATION_ERROR)))
            return@flow
        }

        val request = buildRequest(prompt)

        try {
            AppLogger.d(TAG, "Sending GenerateContent request to model: ${config.geminiModel}")
            val response = geminiApi.generateContent(
                model = config.geminiModel,
                apiKey = config.geminiApiKey,
                body = request,
            )

            val httpCode = response.code()

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    val apiError = body?.error

                    if (apiError != null) {
                        val sanitizedMsg = apiError.message.take(120).replace("\n", " ")
                        AppLogger.e(TAG, "[DEBUG_AI_ERROR] AI provider = CLOUD | HTTP status = $httpCode | Error category = SERVER_ERROR | Error message = $sanitizedMsg")
                        emit(Result.Error(AppError.NetworkError(apiError.code, apiError.message)))
                        return@flow
                    }

                    val text = body?.candidates
                        ?.firstOrNull()
                        ?.content
                        ?.parts
                        ?.joinToString("") { it.text }
                        ?.trim()

                    val finishReason = body?.candidates?.firstOrNull()?.finishReason
                    val totalTokens = body?.usageMetadata?.totalTokenCount ?: 0

                    if (text.isNullOrBlank()) {
                        val category = if (finishReason == "SAFETY") "SAFETY_BLOCKED" else "INVALID_RESPONSE"
                        AppLogger.w(TAG, "[DEBUG_AI_ERROR] AI provider = CLOUD | HTTP status = $httpCode | Error category = $category | Error message = Response text empty (finishReason=$finishReason)")
                        if (finishReason == "SAFETY") {
                            emit(Result.Error(AppError.AIError(AIErrorType.CONTENT_FILTERED)))
                        } else {
                            emit(Result.Error(AppError.AIError(AIErrorType.INFERENCE_FAILED)))
                        }
                        return@flow
                    }

                    AppLogger.d(TAG, "Gemini response OK — ${text.length} chars, $totalTokens tokens")
                    emit(
                        Result.Success(
                            AIChunk(
                                text = text,
                                isComplete = true,
                                totalTokensUsed = totalTokens,
                            )
                        )
                    )
                }

                httpCode == 401 || httpCode == 403 -> {
                    AppLogger.e(TAG, "[DEBUG_AI_ERROR] AI provider = CLOUD | HTTP status = $httpCode | Error category = AUTHENTICATION_ERROR | Error message = Invalid or unauthorized Gemini API key")
                    emit(Result.Error(AppError.AIError(AIErrorType.AUTHENTICATION_ERROR)))
                }

                httpCode == 404 -> {
                    val rawMsg = response.errorBody()?.string()?.take(120) ?: "Model not found"
                    AppLogger.e(TAG, "[DEBUG_AI_ERROR] AI provider = CLOUD | HTTP status = 404 | Error category = MODEL_NOT_FOUND | Error message = Model ${config.geminiModel} not found ($rawMsg)")
                    emit(Result.Error(AppError.AIError(AIErrorType.MODEL_NOT_FOUND)))
                }

                httpCode == 429 -> {
                    AppLogger.w(TAG, "[DEBUG_AI_ERROR] AI provider = CLOUD | HTTP status = 429 | Error category = RATE_LIMITED | Error message = Gemini quota or rate limit exceeded")
                    emit(Result.Error(AppError.AIError(AIErrorType.CLOUD_QUOTA_EXCEEDED)))
                }

                httpCode >= 500 -> {
                    val rawMsg = response.errorBody()?.string()?.take(100) ?: "Server error"
                    AppLogger.e(TAG, "[DEBUG_AI_ERROR] AI provider = CLOUD | HTTP status = $httpCode | Error category = SERVER_ERROR | Error message = Server error: $rawMsg")
                    emit(Result.Error(AppError.NetworkError(httpCode, "Gemini API server error ($httpCode)")))
                }

                else -> {
                    val rawMsg = response.errorBody()?.string()?.take(100) ?: "HTTP error"
                    AppLogger.e(TAG, "[DEBUG_AI_ERROR] AI provider = CLOUD | HTTP status = $httpCode | Error category = UNKNOWN | Error message = $rawMsg")
                    emit(Result.Error(AppError.NetworkError(httpCode, rawMsg)))
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            AppLogger.e(TAG, "[DEBUG_AI_ERROR] AI provider = CLOUD | HTTP status = 408 | Error category = TIMEOUT | Error message = Connection timed out", e)
            emit(Result.Error(AppError.AIError(AIErrorType.TIMEOUT)))
        } catch (e: java.io.IOException) {
            AppLogger.e(TAG, "[DEBUG_AI_ERROR] AI provider = CLOUD | HTTP status = 0 | Error category = NETWORK_ERROR | Error message = Network connection unavailable", e)
            emit(Result.Error(AppError.OfflineError))
        } catch (e: kotlinx.serialization.SerializationException) {
            AppLogger.e(TAG, "[DEBUG_AI_ERROR] AI provider = CLOUD | HTTP status = 200 | Error category = INVALID_RESPONSE | Error message = Response JSON deserialization failed", e)
            emit(Result.Error(AppError.AIError(AIErrorType.INFERENCE_FAILED)))
        } catch (e: Exception) {
            AppLogger.e(TAG, "[DEBUG_AI_ERROR] AI provider = CLOUD | HTTP status = 0 | Error category = UNKNOWN | Error message = ${e.message}", e)
            emit(Result.Error(AppError.UnknownError(e)))
        }
    }

    private fun buildRequest(prompt: AIPrompt): GeminiRequest {
        val contents = prompt.conversationHistory
            .filter { it.role != MessageRole.SYSTEM }
            .map { message ->
                GeminiContent(
                    role = when (message.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "model"
                        MessageRole.SYSTEM -> "user"
                    },
                    parts = listOf(GeminiPart(text = message.content)),
                )
            }
            .toMutableList()

        if (prompt.userMessage.isNotBlank()) {
            contents.add(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = prompt.userMessage)),
                )
            )
        }

        return GeminiRequest(
            contents = contents,
            systemInstruction = GeminiSystemInstruction(
                parts = listOf(GeminiPart(text = prompt.systemInstruction)),
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = prompt.temperature,
                maxOutputTokens = prompt.maxOutputTokens,
            ),
            safetySettings = listOf(
                GeminiSafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_MEDIUM_AND_ABOVE"),
                GeminiSafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_MEDIUM_AND_ABOVE"),
                GeminiSafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_MEDIUM_AND_ABOVE"),
                GeminiSafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_MEDIUM_AND_ABOVE"),
            ),
        )
    }

    private companion object {
        const val TAG = "CloudAIProvider"
    }
}
