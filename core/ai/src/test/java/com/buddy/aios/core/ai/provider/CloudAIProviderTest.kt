package com.buddy.aios.core.ai.provider

import app.cash.turbine.test
import com.buddy.aios.core.ai.config.AIProviderConfig
import com.buddy.aios.core.ai.engine.AIChunk
import com.buddy.aios.core.ai.engine.AIPrompt
import com.buddy.aios.core.domain.result.AIErrorType
import com.buddy.aios.core.domain.result.AppError
import com.buddy.aios.core.domain.result.Result
import com.buddy.aios.core.network.api.GeminiApiService
import com.buddy.aios.core.network.api.GeminiCandidate
import com.buddy.aios.core.network.api.GeminiContent
import com.buddy.aios.core.network.api.GeminiPart
import com.buddy.aios.core.network.api.GeminiResponse
import com.buddy.aios.core.network.api.GeminiUsageMetadata
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class CloudAIProviderTest {

    private lateinit var geminiApi: GeminiApiService
    private lateinit var config: AIProviderConfig
    private lateinit var provider: CloudAIProvider

    @BeforeEach
    fun setUp() {
        geminiApi = mockk()
        config = AIProviderConfig(geminiApiKey = "test_api_key_123", geminiModel = "gemini-2.5-flash")
        provider = CloudAIProvider(geminiApi, config)
    }

    @Test
    fun `isAvailable returns false when API key is blank or empty`() = runTest {
        val emptyConfig = AIProviderConfig(geminiApiKey = "")
        val emptyProvider = CloudAIProvider(geminiApi, emptyConfig)
        assertFalse(emptyProvider.isAvailable())

        assertTrue(provider.isAvailable())
    }

    @Test
    fun `generate missing API key returns authentication error`() = runTest {
        val emptyConfig = AIProviderConfig(geminiApiKey = "")
        val emptyProvider = CloudAIProvider(geminiApi, emptyConfig)
        val prompt = AIPrompt("sys", emptyList(), "hello")

        emptyProvider.generate(prompt).test {
            val item = awaitItem()
            assertTrue(item is Result.Error)
            val error = (item as Result.Error).error
            assertTrue(error is AppError.AIError)
            assertEquals(AIErrorType.AUTHENTICATION_ERROR, (error as AppError.AIError).type)
            awaitComplete()
        }
    }

    @Test
    fun `generate 200 returns successful AI response`() = runTest {
        val mockResponseBody = GeminiResponse(
            candidates = listOf(
                GeminiCandidate(
                    content = GeminiContent(
                        role = "model",
                        parts = listOf(GeminiPart(text = "Docker is a platform for containerizing applications."))
                    ),
                    finishReason = "STOP"
                )
            ),
            usageMetadata = GeminiUsageMetadata(totalTokenCount = 42)
        )
        coEvery { geminiApi.generateContent(any(), any(), any()) } returns Response.success(mockResponseBody)

        val prompt = AIPrompt("sys", emptyList(), "Explain Docker simply")
        provider.generate(prompt).test {
            val item = awaitItem()
            assertTrue(item is Result.Success)
            val chunk = (item as Result.Success).value
            assertEquals("Docker is a platform for containerizing applications.", chunk.text)
            assertTrue(chunk.isComplete)
            assertEquals(42, chunk.totalTokensUsed)
            awaitComplete()
        }
    }

    @Test
    fun `generate 401 returns authentication error`() = runTest {
        val errorResponse = Response.error<GeminiResponse>(401, "Unauthorized".toResponseBody(null))
        coEvery { geminiApi.generateContent(any(), any(), any()) } returns errorResponse

        val prompt = AIPrompt("sys", emptyList(), "hello")
        provider.generate(prompt).test {
            val item = awaitItem()
            assertTrue(item is Result.Error)
            val error = (item as Result.Error).error
            assertTrue(error is AppError.AIError)
            assertEquals(AIErrorType.AUTHENTICATION_ERROR, (error as AppError.AIError).type)
            awaitComplete()
        }
    }

    @Test
    fun `generate 404 returns model not found error`() = runTest {
        val errorResponse = Response.error<GeminiResponse>(404, "Model not found".toResponseBody(null))
        coEvery { geminiApi.generateContent(any(), any(), any()) } returns errorResponse

        val prompt = AIPrompt("sys", emptyList(), "hello")
        provider.generate(prompt).test {
            val item = awaitItem()
            assertTrue(item is Result.Error)
            val error = (item as Result.Error).error
            assertTrue(error is AppError.AIError)
            assertEquals(AIErrorType.MODEL_NOT_FOUND, (error as AppError.AIError).type)
            awaitComplete()
        }
    }

    @Test
    fun `generate 429 returns rate limit error`() = runTest {
        val errorResponse = Response.error<GeminiResponse>(429, "Too Many Requests".toResponseBody(null))
        coEvery { geminiApi.generateContent(any(), any(), any()) } returns errorResponse

        val prompt = AIPrompt("sys", emptyList(), "hello")
        provider.generate(prompt).test {
            val item = awaitItem()
            assertTrue(item is Result.Error)
            val error = (item as Result.Error).error
            assertTrue(error is AppError.AIError)
            assertEquals(AIErrorType.CLOUD_QUOTA_EXCEEDED, (error as AppError.AIError).type)
            awaitComplete()
        }
    }

    @Test
    fun `generate SocketTimeoutException returns timeout error`() = runTest {
        coEvery { geminiApi.generateContent(any(), any(), any()) } throws SocketTimeoutException("timeout")

        val prompt = AIPrompt("sys", emptyList(), "hello")
        provider.generate(prompt).test {
            val item = awaitItem()
            assertTrue(item is Result.Error)
            val error = (item as Result.Error).error
            assertTrue(error is AppError.AIError)
            assertEquals(AIErrorType.TIMEOUT, (error as AppError.AIError).type)
            awaitComplete()
        }
    }

    @Test
    fun `generate 500 returns server error`() = runTest {
        val errorResponse = Response.error<GeminiResponse>(500, "Internal Server Error".toResponseBody(null))
        coEvery { geminiApi.generateContent(any(), any(), any()) } returns errorResponse

        val prompt = AIPrompt("sys", emptyList(), "hello")
        provider.generate(prompt).test {
            val item = awaitItem()
            assertTrue(item is Result.Error)
            val error = (item as Result.Error).error
            assertTrue(error is AppError.NetworkError)
            assertEquals(500, (error as AppError.NetworkError).code)
            awaitComplete()
        }
    }

    @Test
    fun `generate IOException returns offline error`() = runTest {
        coEvery { geminiApi.generateContent(any(), any(), any()) } throws IOException("No network")

        val prompt = AIPrompt("sys", emptyList(), "hello")
        provider.generate(prompt).test {
            val item = awaitItem()
            assertTrue(item is Result.Error)
            val error = (item as Result.Error).error
            assertTrue(error is AppError.OfflineError)
            awaitComplete()
        }
    }
}
