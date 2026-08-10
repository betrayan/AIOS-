package com.buddy.aios.core.data.datasource.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.buddy.aios.core.common.constants.AppConstants
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.PrivacyLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = AppConstants.DATASTORE_PREFERENCES_NAME
)

/**
 * Type-safe DataStore preferences data source.
 */
@Singleton
class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    private val KEY_USER_NAME = stringPreferencesKey("user_name")
    private val KEY_PERSONA_PREFERENCE = stringPreferencesKey("persona_preference")
    private val KEY_PRIVACY_LEVEL = intPreferencesKey("privacy_level")
    private val KEY_BUDDY_MODE = stringPreferencesKey("buddy_mode")

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    val userName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_NAME] ?: ""
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = name
        }
    }

    val privacyLevel: Flow<PrivacyLevel> = context.dataStore.data.map { prefs ->
        val levelInt = prefs[KEY_PRIVACY_LEVEL] ?: 0
        if (levelInt == 1) PrivacyLevel.CLOUD_OPT_IN else PrivacyLevel.LOCAL_ONLY
    }

    suspend fun setPrivacyLevel(level: PrivacyLevel) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PRIVACY_LEVEL] = level.level
        }
    }

    val buddyMode: Flow<BuddyMode> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[KEY_BUDDY_MODE] ?: BuddyMode.ACTIVE.name
        try {
            BuddyMode.valueOf(modeStr)
        } catch (e: Exception) {
            BuddyMode.ACTIVE
        }
    }

    suspend fun setBuddyMode(mode: BuddyMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BUDDY_MODE] = mode.name
        }
    }
}
