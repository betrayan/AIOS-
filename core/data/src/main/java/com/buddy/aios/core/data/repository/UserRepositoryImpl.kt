package com.buddy.aios.core.data.repository

import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.data.datasource.local.PreferencesDataStore
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.entity.UserProfile
import com.buddy.aios.core.domain.repository.IUserRepository
import com.buddy.aios.core.domain.result.AppError
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
    private val dispatchers: DispatcherProvider,
) : IUserRepository {

    override fun observeUserProfile(): Flow<UserProfile> {
        return combine(
            preferencesDataStore.userName,
            preferencesDataStore.privacyLevel,
            preferencesDataStore.isOnboardingCompleted,
        ) { name, privacyLevel, onboardingCompleted ->
            UserProfile(
                id = "local_user",
                name = name.ifBlank { "Buddy User" },
                preferredName = name.ifBlank { "Friend" },
                personaPreference = "companion",
                privacyLevel = privacyLevel,
                onboardingCompleted = onboardingCompleted,
            )
        }.flowOn(dispatchers.io)
    }

    override suspend fun getUserProfile(): Result<UserProfile> {
        return withContext(dispatchers.io) {
            try {
                val profile = observeUserProfile().first()
                Result.Success(profile)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
        return withContext(dispatchers.io) {
            try {
                preferencesDataStore.setUserName(profile.name)
                preferencesDataStore.setPrivacyLevel(profile.privacyLevel)
                preferencesDataStore.setOnboardingCompleted(profile.onboardingCompleted)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }

    override suspend fun setPrivacyLevel(level: PrivacyLevel): Result<Unit> {
        return withContext(dispatchers.io) {
            try {
                preferencesDataStore.setPrivacyLevel(level)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(AppError.StorageError(e))
            }
        }
    }
}
