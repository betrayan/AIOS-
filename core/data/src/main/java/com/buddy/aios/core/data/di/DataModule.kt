package com.buddy.aios.core.data.di

import com.buddy.aios.core.data.repository.BuddyModeRepositoryImpl
import com.buddy.aios.core.data.repository.ConversationRepositoryImpl
import com.buddy.aios.core.data.repository.MemoryRepositoryImpl
import com.buddy.aios.core.data.repository.TaskRepositoryImpl
import com.buddy.aios.core.data.repository.UserRepositoryImpl
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IConversationRepository
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.repository.ITaskRepository
import com.buddy.aios.core.domain.repository.IUserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        impl: ConversationRepositoryImpl,
    ): IConversationRepository

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(
        impl: MemoryRepositoryImpl,
    ): IMemoryRepository

    @Binds
    @Singleton
    abstract fun bindBuddyModeRepository(
        impl: BuddyModeRepositoryImpl,
    ): IBuddyModeRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        impl: TaskRepositoryImpl,
    ): ITaskRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        impl: UserRepositoryImpl,
    ): IUserRepository
}
