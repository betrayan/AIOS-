package com.buddy.aios.feature.voice.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VoicePrefs

@Module
@InstallIn(SingletonComponent::class)
object VoicePrefsModule {

    @Provides
    @Singleton
    @VoicePrefs
    fun provideVoicePrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("voice_recording_prefs", Context.MODE_PRIVATE)
}
