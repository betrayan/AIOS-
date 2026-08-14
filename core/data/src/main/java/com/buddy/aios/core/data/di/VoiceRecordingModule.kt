package com.buddy.aios.core.data.di

import com.buddy.aios.core.data.repository.VoiceRecordingRepositoryImpl
import com.buddy.aios.core.domain.repository.IVoiceRecordingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceRecordingModule {

    @Binds
    @Singleton
    abstract fun bindVoiceRecordingRepository(
        impl: VoiceRecordingRepositoryImpl,
    ): IVoiceRecordingRepository
}
