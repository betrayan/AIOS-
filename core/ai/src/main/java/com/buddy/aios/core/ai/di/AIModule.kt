package com.buddy.aios.core.ai.di

import com.buddy.aios.core.ai.engine.AIEngine
import com.buddy.aios.core.ai.engine.AIOrchestrator
import com.buddy.aios.core.ai.policy.AIPolicy
import com.buddy.aios.core.ai.policy.DefaultAIPolicy
import com.buddy.aios.core.ai.provider.AIProvider
import com.buddy.aios.core.ai.provider.CloudAIProvider
import com.buddy.aios.core.ai.provider.LocalAIProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AIModule {

    /** Bind the real orchestrator as the AIEngine — replaces DefaultAIEngine stub. */
    @Binds
    @Singleton
    abstract fun bindAIEngine(
        impl: AIOrchestrator,
    ): AIEngine

    /** Bind the production AIPolicy with BuddyMode + privacy enforcement. */
    @Binds
    @Singleton
    abstract fun bindAIPolicy(
        impl: DefaultAIPolicy,
    ): AIPolicy

    /** On-device Gemma provider (reports unavailable until model asset is present). */
    @Binds
    @Singleton
    @Named("local")
    abstract fun bindLocalAIProvider(
        impl: LocalAIProvider,
    ): AIProvider

    /** Cloud Gemini provider (active only when CLOUD_OPT_IN and API key configured). */
    @Binds
    @Singleton
    @Named("cloud")
    abstract fun bindCloudAIProvider(
        impl: CloudAIProvider,
    ): AIProvider
}
