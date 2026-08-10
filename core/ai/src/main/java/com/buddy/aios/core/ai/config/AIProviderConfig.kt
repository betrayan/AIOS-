package com.buddy.aios.core.ai.config

/**
 * Runtime configuration injected from :app's BuildConfig.
 *
 * This class bridges :app-level build secrets into :core:ai without
 * creating a direct :core:ai → :app dependency.
 *
 * Provided by [com.buddy.aios.di.AIConfigModule] in the :app module.
 */
data class AIProviderConfig(
    /**
     * Gemini API key. Empty string means Cloud AI is unavailable.
     * Never hard-coded. Injected from local.properties → BuildConfig.
     */
    val geminiApiKey: String,

    /**
     * Gemini model identifier.
     * Default: gemini-2.0-flash-exp (fast, capable, free tier available)
     */
    val geminiModel: String = "gemini-flash-latest",

    /**
     * Absolute path to the on-device Gemma model file (.task or .bin).
     * Empty string means LocalAIProvider reports unavailable.
     * Set once the user downloads the model.
     */
    val gemmaModelPath: String = "",
)
