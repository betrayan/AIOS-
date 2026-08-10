package com.buddy.aios.core.common.constants

/**
 * Global application constants.
 */
object AppConstants {

    // App Information
    const val APP_NAME = "AIOS"
    const val PACKAGE_NAME = "com.buddy.aios"

    // Database
    const val DATABASE_NAME = "buddy_ai_os.db"
    const val DATABASE_VERSION = 1

    // DataStore
    const val DATASTORE_PREFERENCES_NAME = "buddy_preferences"

    // WorkManager Task Names
    const val WORK_CONVERSATION_SYNC = "work_conversation_sync"
    const val WORK_MEMORY_DECAY = "work_memory_decay"
    const val WORK_PROACTIVE_CHECKIN = "work_proactive_checkin"

    // Security & Encryption
    const val KEYSTORE_ALIAS = "buddy_aios_master_key"
    const val ENCRYPTION_TRANSFORMATION = "AES/GCM/NoPadding"

    // AI & Performance Limits
    const val MAX_CONTEXT_TOKENS = 4096
    const val SUMMARIZATION_THRESHOLD_RATIO = 0.80f
    const val MAX_RECENT_MESSAGES_FOR_CONTEXT = 20

    // Time & Timeout Constants
    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val NETWORK_RETRY_ATTEMPTS = 3
}
