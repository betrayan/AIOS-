package com.buddy.aios.core.common.logging

/**
 * Logging abstraction — prevents direct Logcat usage in release builds
 * and enables log capture in tests.
 */
interface BuddyLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

/** No-op logger for release builds and tests that don't care about logs. */
object NoOpLogger : BuddyLogger {
    override fun d(tag: String, message: String) = Unit
    override fun i(tag: String, message: String) = Unit
    override fun w(tag: String, message: String, throwable: Throwable?) = Unit
    override fun e(tag: String, message: String, throwable: Throwable?) = Unit
}

/**
 * Production logger — delegates to android.util.Log on Android device/emulator,
 * and falls back gracefully to stdout/stderr in JVM unit tests without crashing.
 */
object AppLogger : BuddyLogger {
    override fun d(tag: String, message: String) {
        try {
            android.util.Log.d(tag, message)
        } catch (_: Throwable) {
            println("[$tag] $message")
        }
    }

    override fun i(tag: String, message: String) {
        try {
            android.util.Log.i(tag, message)
        } catch (_: Throwable) {
            println("[$tag] $message")
        }
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        try {
            android.util.Log.w(tag, message, throwable)
        } catch (_: Throwable) {
            println("[$tag] $message ${throwable?.message ?: ""}")
        }
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        try {
            android.util.Log.e(tag, message, throwable)
        } catch (_: Throwable) {
            System.err.println("[$tag] $message ${throwable?.message ?: ""}")
        }
    }
}
