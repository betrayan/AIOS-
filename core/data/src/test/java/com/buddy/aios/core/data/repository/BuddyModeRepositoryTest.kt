package com.buddy.aios.core.data.repository

import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.data.datasource.local.PreferencesDataStore
import com.buddy.aios.core.domain.entity.BuddyMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BuddyModeRepositoryTest {

    private val preferencesDataStore: PreferencesDataStore = mockk(relaxed = true)
    private val dispatchers: DispatcherProvider = mockk()
    private lateinit var repository: BuddyModeRepositoryImpl

    @BeforeEach
    fun setUp() {
        every { dispatchers.io } returns Dispatchers.Unconfined
        every { dispatchers.main } returns Dispatchers.Unconfined
        every { dispatchers.default } returns Dispatchers.Unconfined
        repository = BuddyModeRepositoryImpl(preferencesDataStore, dispatchers)
    }

    @Test
    fun `observeBuddyMode emits mode from preferences data store`() = runTest {
        every { preferencesDataStore.buddyMode } returns flowOf(BuddyMode.QUIET)

        val mode = repository.observeBuddyMode().first()

        assertEquals(BuddyMode.QUIET, mode)
    }

    @Test
    fun `setBuddyMode delegates to preferences data store`() = runTest {
        coEvery { preferencesDataStore.setBuddyMode(BuddyMode.SILENT) } returns Unit

        repository.setBuddyMode(BuddyMode.SILENT)

        coVerify { preferencesDataStore.setBuddyMode(BuddyMode.SILENT) }
    }
}
