package com.buddy.aios.core.ai.policy

import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.PrivacyLevel
import javax.inject.Inject

/**
 * Determines what AI operations are permitted and which provider to use.
 *
 * Two responsibilities:
 * 1. [canExecuteTextAI] — BuddyMode gate (OFF = no AI at all)
 * 2. [shouldUseCloud]   — Provider routing (LOCAL_ONLY vs CLOUD_OPT_IN)
 */
interface AIPolicy {
    /**
     * Returns true if text AI is allowed to execute in the given [buddyMode].
     * - ACTIVE, QUIET, SILENT → text AI permitted
     * - OFF → no AI execution at all
     */
    fun canExecuteTextAI(buddyMode: BuddyMode): Boolean

    /**
     * Returns true if the cloud AI provider should be used for this request.
     * Always returns false if [AIRoutingContext.privacyLevel] is LOCAL_ONLY.
     */
    fun shouldUseCloud(context: AIRoutingContext): Boolean
}

/**
 * All inputs needed to make an AI routing decision.
 */
data class AIRoutingContext(
    val buddyMode: BuddyMode,
    val privacyLevel: PrivacyLevel,
    val isNetworkAvailable: Boolean,
    val isOnDeviceModelLoaded: Boolean,
    val messageComplexityScore: Float = 0.5f,  // 0.0 = simple, 1.0 = highly complex
    val onDeviceInferenceTooSlow: Boolean = false,
    val cloudQuotaExceeded: Boolean = false,
)

/**
 * Production implementation of [AIPolicy].
 *
 * Privacy rules (highest priority):
 * - DEFAULT privacy is LOCAL_ONLY — cloud AI requires explicit CLOUD_OPT_IN.
 * - LOCAL_ONLY: never send data to cloud, regardless of network or capability.
 *
 * BuddyMode rules:
 * - OFF: no text AI execution.
 * - QUIET / SILENT / ACTIVE: text AI is permitted.
 *
 * Provider routing (in priority order):
 * 1. BuddyMode OFF → blocked (no provider selected)
 * 2. Privacy = LOCAL_ONLY → on-device only (or unavailable)
 * 3. No network → on-device only
 * 4. Cloud quota exceeded → on-device fallback
 * 5. On-device not loaded → cloud (if CLOUD_OPT_IN)
 * 6. Default → on-device (battery + privacy preferred)
 */
class DefaultAIPolicy @Inject constructor() : AIPolicy {

    override fun canExecuteTextAI(buddyMode: BuddyMode): Boolean {
        return buddyMode != BuddyMode.OFF
    }

    override fun shouldUseCloud(context: AIRoutingContext): Boolean {
        // BuddyMode OFF blocks everything
        if (context.buddyMode == BuddyMode.OFF) return false

        // Privacy is the highest-priority gate for cloud
        // Default is LOCAL_ONLY — cloud requires explicit user consent
        if (context.privacyLevel == PrivacyLevel.LOCAL_ONLY) return false

        // Infrastructure requirements for cloud
        if (!context.isNetworkAvailable) return false
        if (context.cloudQuotaExceeded) return false

        // Prefer on-device when it's loaded and healthy
        if (context.isOnDeviceModelLoaded && !context.onDeviceInferenceTooSlow) return false

        // On-device unavailable → use cloud (user has consented via CLOUD_OPT_IN)
        return true
    }
}
