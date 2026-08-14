package com.buddy.aios.core.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.buddy.aios.core.domain.entity.MorningBriefingSettings
import com.buddy.aios.core.domain.repository.IMorningBriefingSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MorningBriefingSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : IMorningBriefingSettingsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aios_morning_intelligence_prefs", Context.MODE_PRIVATE)

    private val _settingsState = MutableStateFlow(readSettings())

    private fun readSettings(): MorningBriefingSettings {
        return MorningBriefingSettings(
            isBriefingEnabled = prefs.getBoolean("is_briefing_enabled", true),
            isVoiceEnabled = prefs.getBoolean("is_voice_enabled", true),
            includeWeather = prefs.getBoolean("include_weather", true),
            includeSleep = prefs.getBoolean("include_sleep", true),
            contextualBatteryAlerts = prefs.getBoolean("contextual_battery_alerts", true),
            contextualTravelAlerts = prefs.getBoolean("contextual_travel_alerts", true),
            importantReminderVoice = prefs.getBoolean("important_reminder_voice", true),
            allowScreenOffVoice = prefs.getBoolean("allow_screen_off_voice", false),
            isMorningWishEnabled = prefs.getBoolean("is_morning_wish_enabled", true),
            morningWishHour = prefs.getInt("morning_wish_hour", 6),
            morningWishMinute = prefs.getInt("morning_wish_minute", 0),
        )
    }

    override fun observeSettings(): Flow<MorningBriefingSettings> = _settingsState.asStateFlow()

    override suspend fun getSettings(): MorningBriefingSettings = withContext(Dispatchers.IO) {
        readSettings()
    }

    override suspend fun updateSettings(settings: MorningBriefingSettings) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putBoolean("is_briefing_enabled", settings.isBriefingEnabled)
            .putBoolean("is_voice_enabled", settings.isVoiceEnabled)
            .putBoolean("include_weather", settings.includeWeather)
            .putBoolean("include_sleep", settings.includeSleep)
            .putBoolean("contextual_battery_alerts", settings.contextualBatteryAlerts)
            .putBoolean("contextual_travel_alerts", settings.contextualTravelAlerts)
            .putBoolean("important_reminder_voice", settings.importantReminderVoice)
            .putBoolean("allow_screen_off_voice", settings.allowScreenOffVoice)
            .putBoolean("is_morning_wish_enabled", settings.isMorningWishEnabled)
            .putInt("morning_wish_hour", settings.morningWishHour)
            .putInt("morning_wish_minute", settings.morningWishMinute)
            .apply()
        _settingsState.value = settings
    }
}
