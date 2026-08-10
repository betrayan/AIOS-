package com.buddy.aios.di

import com.buddy.aios.BuildConfig
import com.buddy.aios.core.ai.config.AIProviderConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AIConfigModule {

    @Provides
    @Singleton
    fun provideAIProviderConfig(): AIProviderConfig {
        return AIProviderConfig(
            geminiApiKey = BuildConfig.GEMINI_API_KEY,
            geminiModel = "gemini-flash-latest",
            gemmaModelPath = BuildConfig.GEMMA_MODEL_PATH,
        )
    }
}
