package com.buddy.aios.core.data.repository

import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.data.datasource.local.PreferencesDataStore
import com.buddy.aios.core.domain.entity.BuddyCapability
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.getCapabilities
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuddyModeRepositoryImpl @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
    private val dispatchers: DispatcherProvider,
) : IBuddyModeRepository {

    override fun observeBuddyMode(): Flow<BuddyMode> {
        return preferencesDataStore.buddyMode
            .flowOn(dispatchers.io)
    }

    override fun observeCapabilities(): Flow<BuddyCapability> {
        return preferencesDataStore.buddyMode
            .map { it.getCapabilities() }
            .flowOn(dispatchers.io)
    }

    override suspend fun getBuddyMode(): BuddyMode {
        return withContext(dispatchers.io) {
            preferencesDataStore.buddyMode.first()
        }
    }

    override suspend fun getCapabilities(): BuddyCapability {
        return getBuddyMode().getCapabilities()
    }

    override suspend fun setBuddyMode(mode: BuddyMode) {
        withContext(dispatchers.io) {
            preferencesDataStore.setBuddyMode(mode)
        }
    }
}
