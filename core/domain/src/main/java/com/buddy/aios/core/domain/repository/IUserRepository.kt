package com.buddy.aios.core.domain.repository

import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.entity.UserProfile
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user profile & system preferences.
 */
interface IUserRepository {

    /** Observe the current [UserProfile]. */
    fun observeUserProfile(): Flow<UserProfile>

    /** Get current snapshot of [UserProfile]. */
    suspend fun getUserProfile(): Result<UserProfile>

    /** Update user profile properties. */
    suspend fun updateUserProfile(profile: UserProfile): Result<Unit>

    /** Update privacy level setting. */
    suspend fun setPrivacyLevel(level: PrivacyLevel): Result<Unit>
}
