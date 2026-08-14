package com.buddy.aios.core.ai.summary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SummaryValidatorTest {

    private lateinit var validator: SummaryValidator

    @BeforeEach
    fun setUp() {
        validator = SummaryValidator()
    }

    @Test
    fun `TEST 1 - Faithful summary passes validation unchanged`() {
        val full = "Docker packages your application into a container so it runs consistently across environments. Images are immutable templates."
        val candidate = "Docker packages your application into a container so it runs consistently."

        val sanitized = validator.validateAndSanitize(full, candidate)

        assertEquals(candidate, sanitized)
    }

    @Test
    fun `TEST 2 - Summary with contradictory number falls back to extractive summary`() {
        val full = "Your phone battery is at 18 percent right now."
        val candidate = "Your battery is at 80 percent." // Contradiction!

        val sanitized = validator.validateAndSanitize(full, candidate)

        assertFalse(sanitized.contains("80"), "Contradictory number 80% must be stripped")
        assertTrue(sanitized.contains("18"), "Extractive summary must contain true fact 18%")
    }

    @Test
    fun `TEST 3 - Summary with contradictory time falls back to extractive summary`() {
        val full = "Your team meeting is scheduled for 4 PM today."
        val candidate = "Your meeting is at 5 PM." // Contradiction!

        val sanitized = validator.validateAndSanitize(full, candidate)

        assertFalse(sanitized.contains("5 PM"), "Contradictory time 5 PM must be stripped")
        assertTrue(sanitized.contains("4 PM"), "Extractive summary must retain true time 4 PM")
    }

    @Test
    fun `TEST 4 - Extractive summary length is bounded`() {
        val full = "Docker is a containerization platform. It packages applications with dependencies. Containers are isolated. Images are read-only. Docker Compose handles multi-container setups. Docker Swarm provides orchestration."

        val summary = validator.extractSummary(full, maxWords = 20)

        assertTrue(summary.isNotBlank())
        assertTrue(summary.split("\\s+".toRegex()).size <= 25)
    }
}
