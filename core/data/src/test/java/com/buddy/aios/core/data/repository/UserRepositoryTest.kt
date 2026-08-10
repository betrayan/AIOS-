package com.buddy.aios.core.data.repository

import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.data.datasource.local.PreferencesDataStore
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.result.Result
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserRepositoryTest {

    private val preferencesDataStore: PreferencesDataStore = mockk(relaxed = true)
    private val dispatchers: DispatcherProvider = mockk()
    private lateinit var repository: UserRepositoryImpl

    @BeforeEach
    fun setUp() {
        every { dispatchers.io } returns Dispatchers.Unconfined
        every { dispatchers.main } returns Dispatchers.Unconfined
        every { dispatchers.default } returns Dispatchers.Unconfined
        repository = UserRepositoryImpl(preferencesDataStore, dispatchers)
    }

    @Test
    fun `observeUserProfile combines DataStore preferences into UserProfile`() = runTest {
        every { preferencesDataStore.userName } returns flowOf("Alex")
        every { preferencesDataStore.privacyLevel } returns flowOf(PrivacyLevel.LOCAL_ONLY)
        every { preferencesDataStore.isOnboardingCompleted } returns flowOf(true)

        val profile = repository.observeUserProfile().first()

        assertEquals("Alex", profile.name)
        assertEquals(PrivacyLevel.LOCAL_ONLY, profile.privacyLevel)
        assertTrue(profile.onboardingCompleted)
    }

    @Test
    fun `setPrivacyLevel updates DataStore`() = runTest {
        coEvery { preferencesDataStore.setPrivacyLevel(PrivacyLevel.CLOUD_OPT_IN) } returns Unit

        val result = repository.setPrivacyLevel(PrivacyLevel.CLOUD_OPT_IN)

        assertTrue(result is Result.Success)
        coVerify { preferencesDataStore.setPrivacyLevel(PrivacyLevel.CLOUD_OPT_IN) }
    }
}
