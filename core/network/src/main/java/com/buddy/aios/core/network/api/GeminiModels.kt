package com.buddy.aios.core.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Request ─────────────────────────────────────────────────────────────────

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("system_instruction")
    val systemInstruction: GeminiSystemInstruction? = null,
    @SerialName("generationConfig")
    val generationConfig: GeminiGenerationConfig? = null,
    @SerialName("safetySettings")
    val safetySettings: List<GeminiSafetySetting>? = null,
)

@Serializable
data class GeminiContent(
    val role: String,   // "user" | "model"
    val parts: List<GeminiPart>,
)

@Serializable
data class GeminiPart(
    val text: String,
)

@Serializable
data class GeminiSystemInstruction(
    val parts: List<GeminiPart>,
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float = 0.7f,
    @SerialName("maxOutputTokens")
    val maxOutputTokens: Int = 2048,
    @SerialName("topP")
    val topP: Float = 0.95f,
)

@Serializable
data class GeminiSafetySetting(
    val category: String,
    val threshold: String,
)

// ─── Response ────────────────────────────────────────────────────────────────

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    @SerialName("usageMetadata")
    val usageMetadata: GeminiUsageMetadata? = null,
    val error: GeminiApiError? = null,
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerialName("finishReason")
    val finishReason: String? = null,
)

@Serializable
data class GeminiUsageMetadata(
    @SerialName("promptTokenCount")
    val promptTokenCount: Int = 0,
    @SerialName("candidatesTokenCount")
    val candidatesTokenCount: Int = 0,
    @SerialName("totalTokenCount")
    val totalTokenCount: Int = 0,
)

@Serializable
data class GeminiApiError(
    val code: Int = 0,
    val message: String = "",
    val status: String = "",
)
