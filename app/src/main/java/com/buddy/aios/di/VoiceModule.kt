package com.buddy.aios.di

import com.buddy.aios.core.common.voice.IVoiceOutputManager
import com.buddy.aios.feature.chat.voice.TextToSpeechManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for IVoiceOutputManager → TextToSpeechManager.
 *
 * This binding lives in the app module because:
 *  - IVoiceOutputManager is declared in core:common (lower-level)
 *  - TextToSpeechManager is declared in feature:chat (upper-level)
 *  - Only the app module has visibility over both layers simultaneously.
 *
 * Workers and other lower-level modules depend only on IVoiceOutputManager.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindVoiceOutputManager(
        impl: TextToSpeechManager,
    ): IVoiceOutputManager
}
