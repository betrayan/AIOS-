package com.buddy.aios.workers.di

import com.buddy.aios.core.domain.repository.IReminderScheduler
import com.buddy.aios.workers.notification.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SchedulerModule {

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(
        impl: ReminderScheduler
    ): IReminderScheduler
}
