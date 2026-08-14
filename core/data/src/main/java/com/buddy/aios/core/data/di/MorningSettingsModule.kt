package com.buddy.aios.core.data.di

import com.buddy.aios.core.data.repository.MorningBriefingSettingsRepositoryImpl
import com.buddy.aios.core.domain.repository.IMorningBriefingSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MorningSettingsModule {

    @Binds
    @Singleton
    abstract fun bindMorningBriefingSettingsRepository(
        impl: MorningBriefingSettingsRepositoryImpl
    ): IMorningBriefingSettingsRepository
}
