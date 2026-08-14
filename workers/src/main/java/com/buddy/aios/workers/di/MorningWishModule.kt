package com.buddy.aios.workers.di

import com.buddy.aios.core.domain.repository.IMorningWishEngine
import com.buddy.aios.workers.morning.MorningWishEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MorningWishModule {

    @Binds
    @Singleton
    abstract fun bindMorningWishEngine(
        impl: MorningWishEngine
    ): IMorningWishEngine
}
