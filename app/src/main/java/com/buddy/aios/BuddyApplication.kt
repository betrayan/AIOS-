package com.buddy.aios

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Responsibilities:
 * - Triggers Hilt component generation (@HiltAndroidApp)
 * - Provides custom WorkManager configuration (required for HiltWorkerFactory)
 * - Initialises crash reporting via Firebase Crashlytics (see CrashlyticsInitializer)
 */
@HiltAndroidApp
class BuddyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG
                else android.util.Log.ERROR
            )
            .build()
}
