package com.buddy.aios.core.ai.policy

import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.PrivacyLevel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AIPolicyTest {

    private lateinit var policy: DefaultAIPolicy

    @BeforeEach
    fun setUp() {
        policy = DefaultAIPolicy()
    }

    @Test
    fun `canExecuteTextAI returns false when BuddyMode is OFF`() {
        assertFalse(policy.canExecuteTextAI(BuddyMode.OFF))
    }

    @Test
    fun `canExecuteTextAI returns true when BuddyMode is ACTIVE, QUIET, or SILENT`() {
        assertTrue(policy.canExecuteTextAI(BuddyMode.ACTIVE))
        assertTrue(policy.canExecuteTextAI(BuddyMode.QUIET))
        assertTrue(policy.canExecuteTextAI(BuddyMode.SILENT))
    }

    @Test
    fun `shouldUseCloud returns false when privacy level is LOCAL_ONLY`() {
        val context = AIRoutingContext(
            buddyMode = BuddyMode.ACTIVE,
            privacyLevel = PrivacyLevel.LOCAL_ONLY,
            isNetworkAvailable = true,
            isOnDeviceModelLoaded = false,
        )
        assertFalse(policy.shouldUseCloud(context))
    }

    @Test
    fun `shouldUseCloud returns true when CLOUD_OPT_IN, network available, and local model not loaded`() {
        val context = AIRoutingContext(
            buddyMode = BuddyMode.ACTIVE,
            privacyLevel = PrivacyLevel.CLOUD_OPT_IN,
            isNetworkAvailable = true,
            isOnDeviceModelLoaded = false,
        )
        assertTrue(policy.shouldUseCloud(context))
    }

    @Test
    fun `shouldUseCloud returns false when network is unavailable even with CLOUD_OPT_IN`() {
        val context = AIRoutingContext(
            buddyMode = BuddyMode.ACTIVE,
            privacyLevel = PrivacyLevel.CLOUD_OPT_IN,
            isNetworkAvailable = false,
            isOnDeviceModelLoaded = false,
        )
        assertFalse(policy.shouldUseCloud(context))
    }
}
