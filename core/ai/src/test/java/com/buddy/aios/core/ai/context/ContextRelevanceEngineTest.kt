package com.buddy.aios.core.ai.context

import com.buddy.aios.core.ai.morning.MorningContextEngine
import com.buddy.aios.core.analytics.activity.DeviceActivityManager
import com.buddy.aios.core.analytics.activity.EstimatedSleepEstimate
import com.buddy.aios.core.analytics.activity.SleepActivityInference
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority
import com.buddy.aios.core.domain.entity.UserProfile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContextRelevanceEngineTest {

    private lateinit var relevanceEngine: ContextRelevanceEngine
    private lateinit var suggestionEngine: DailySuggestionEngine
    private lateinit var morningContextEngine: MorningContextEngine

    private val activityManager: DeviceActivityManager = mockk(relaxed = true)
    private val sleepInference: SleepActivityInference = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        relevanceEngine = ContextRelevanceEngine()
        suggestionEngine = DailySuggestionEngine()

        every { activityManager.snapshot } returns MutableStateFlow(mockk(relaxed = true))
        every { sleepInference.inferSleepEstimate(any(), any()) } returns EstimatedSleepEstimate(hasSufficientData = false)

        morningContextEngine = MorningContextEngine(
            activityManager = activityManager,
            sleepInference = sleepInference,
            relevanceEngine = relevanceEngine,
            suggestionEngine = suggestionEngine,
        )
    }

    @Test
    fun `TEST 1 - Battery at 85 percent on normal day is omitted from context`() {
        val payload = RawContextPayload(
            batteryLevel = 85,
            tasks = listOf(Task(id = "t1", title = "Read book", createdAt = 100L)),
        )

        val items = relevanceEngine.analyzeContext(payload)
        val batteryItem = items.firstOrNull { it.type == ContextType.BATTERY }

        assertNull(batteryItem, "Normal battery (85%) must be omitted from context")
    }

    @Test
    fun `TEST 2 - Battery at 18 percent with travel context receives high priority and relevance`() {
        val payload = RawContextPayload(
            batteryLevel = 18,
            tasks = listOf(Task(id = "t1", title = "Travel to Chennai tomorrow", createdAt = 100L)),
        )

        val items = relevanceEngine.analyzeContext(payload)
        val batteryItem = items.firstOrNull { it.type == ContextType.BATTERY }

        assertNotNull(batteryItem, "Low battery with travel must be included")
        assertTrue(batteryItem!!.relevanceScore >= 0.8f)
        assertEquals(ContextPriority.HIGH, batteryItem.priority)
    }

    @Test
    fun `TEST 3 - Weather with Rain is included with conversational advice`() {
        val payload = RawContextPayload(
            weatherCondition = "Rain",
            temperatureCelsius = 24,
            tasks = listOf(Task(id = "t1", title = "Go to college", createdAt = 100L)),
        )

        val items = relevanceEngine.analyzeContext(payload)
        val weatherItem = items.firstOrNull { it.type == ContextType.WEATHER }

        assertNotNull(weatherItem)
        assertTrue(weatherItem!!.description.contains("umbrella", ignoreCase = true))
    }

    @Test
    fun `TEST 4 - Travel Context detection from task title`() {
        val payload = RawContextPayload(
            tasks = listOf(
                Task(id = "t1", title = "Flight to Bangalore at 6 AM", createdAt = 100L)
            )
        )

        val items = relevanceEngine.analyzeContext(payload)
        val travelItem = items.firstOrNull { it.type == ContextType.TRAVEL }

        assertNotNull(travelItem)
        assertEquals(DayClassification.TRAVEL_DAY, relevanceEngine.classifyDay(items, payload.tasks))
    }

    @Test
    fun `TEST 5 - Day Classification for multiple coding tasks returns CODING_DAY`() {
        val tasks = listOf(
            Task(id = "t1", title = "Java practice", createdAt = 100L),
            Task(id = "t2", title = "AIOS project work", createdAt = 100L),
            Task(id = "t3", title = "DSA problems", createdAt = 100L),
        )
        val payload = RawContextPayload(tasks = tasks)
        val items = relevanceEngine.analyzeContext(payload)

        val classification = relevanceEngine.classifyDay(items, tasks)
        assertEquals(DayClassification.CODING_DAY, classification)
    }

    @Test
    fun `TEST 6 - Day Classification for multiple study tasks returns STUDY_DAY`() {
        val tasks = listOf(
            Task(id = "t1", title = "Math Exam revision", createdAt = 100L),
            Task(id = "t2", title = "College assignment", createdAt = 100L),
        )
        val payload = RawContextPayload(tasks = tasks)
        val items = relevanceEngine.analyzeContext(payload)

        val classification = relevanceEngine.classifyDay(items, tasks)
        assertEquals(DayClassification.STUDY_DAY, classification)
    }

    @Test
    fun `TEST 7 - Morning Briefing generation is offline safe and produces natural voice summary`() {
        val profile = UserProfile(id = "1", name = "Buddy", preferredName = "Buddy", personaPreference = "default")
        val tasks = listOf(
            Task(id = "t1", title = "Java practice", createdAt = 100L, priority = TaskPriority.HIGH),
            Task(id = "t2", title = "Project work", createdAt = 100L),
        )

        val briefing = morningContextEngine.generateMorningBriefing(
            userProfile = profile,
            activeTasks = tasks,
            memories = emptyList(),
            batteryLevel = 80, // Normal battery -> should not be mentioned
            weatherCondition = "Clear",
        )

        assertNotNull(briefing)
        assertTrue(briefing.spokenBriefing.contains("Buddy"))
        assertTrue(briefing.spokenBriefing.contains("Java practice"))
        assertFalse(briefing.spokenBriefing.contains("battery", ignoreCase = true), "Normal battery should be omitted from voice summary")
    }
}
