package com.buddy.aios.core.network.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the Gemini REST API.
 *
 * Endpoint: https://generativelanguage.googleapis.com/v1beta/
 * Documentation: https://ai.google.dev/api/generate-content
 *
 * The API key is passed as a query parameter — never in source code.
 * Retrofit base URL and key injection are handled in [NetworkModule].
 */
interface GeminiApiService {

    /**
     * Generates content using the specified Gemini model.
     *
     * @param model  Model identifier, e.g. "gemini-2.0-flash-exp"
     * @param apiKey Gemini API key (from BuildConfig, never hardcoded)
     * @param body   The full generation request including conversation history
     */
    @POST("models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body body: GeminiRequest,
    ): Response<GeminiResponse>
}
