package com.buddy.aios.core.domain.repository

import com.buddy.aios.core.domain.entity.MorningBriefingSettings
import kotlinx.coroutines.flow.Flow

interface IMorningBriefingSettingsRepository {
    fun observeSettings(): Flow<MorningBriefingSettings>
    suspend fun getSettings(): MorningBriefingSettings
    suspend fun updateSettings(settings: MorningBriefingSettings)
}
