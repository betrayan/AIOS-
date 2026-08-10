package com.buddy.aios.core.security.di

import com.buddy.aios.core.security.EncryptionService
import com.buddy.aios.core.security.KeystoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager = KeystoreManager()

    @Provides
    @Singleton
    fun provideEncryptionService(
        keystoreManager: KeystoreManager,
    ): EncryptionService = EncryptionService(keystoreManager)
}
