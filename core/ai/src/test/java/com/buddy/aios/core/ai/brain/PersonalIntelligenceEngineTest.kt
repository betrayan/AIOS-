package com.buddy.aios.core.ai.brain

import com.buddy.aios.core.ai.agent.GoalAnalyzer
import com.buddy.aios.core.ai.context.ConversationContextManager
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority
import com.buddy.aios.core.domain.entity.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PersonalIntelligenceEngineTest {

    private lateinit var situationDetector: SituationDetector
    private lateinit var contextScorer: ContextScorer
    private lateinit var priorityEngine: PriorityEngine
    private lateinit var decisionEngine: DecisionEngine
    private lateinit var goalAnalyzer: GoalAnalyzer
    private lateinit var proactivePolicy: ProactiveNotificationPolicy
    private lateinit var conversationContextManager: ConversationContextManager
    private lateinit var intelligenceEngine: PersonalIntelligenceEngine

    @BeforeEach
    fun setUp() {
        situationDetector = SituationDetector()
        contextScorer = ContextScorer()
        priorityEngine = PriorityEngine()
        decisionEngine = DecisionEngine(priorityEngine)
        goalAnalyzer = GoalAnalyzer()
        proactivePolicy = ProactiveNotificationPolicy()
        conversationContextManager = ConversationContextManager()

        intelligenceEngine = PersonalIntelligenceEngine(
            situationDetector = situationDetector,
            contextScorer = contextScorer,
            priorityEngine = priorityEngine,
            decisionEngine = decisionEngine,
            goalAnalyzer = goalAnalyzer,
            proactivePolicy = proactivePolicy,
            conversationContextManager = conversationContextManager,
        )
    }

    @Test
    fun `TEST 1 - What should I do now selects top priority task without dumping all tasks`() = runTest {
        val tasks = listOf(
            Task(id = "t1", title = "Java Practice", createdAt = 100L, priority = TaskPriority.HIGH),
            Task(id = "t2", title = "Read Book", createdAt = 100L),
        )
        val snapshot = intelligenceEngine.buildSnapshot(
            userProfile = UserProfile("1", "Vijay", "Vijay", "default"),
            buddyMode = BuddyMode.ACTIVE,
            activeTasks = tasks,
            memories = emptyList(),
        )

        val decision = intelligenceEngine.processQuery("What should I do now?", snapshot)

        assertEquals(ActionType.ANSWER, decision.actionType)
        assertTrue(decision.primaryTextResponse.contains("Java Practice"))
        assertFalse(decision.primaryTextResponse.contains("Read Book"), "Should focus on top priority task without dumping full task list")
    }

    @Test
    fun `TEST 2 - What is important today returns top 3 priorities`() = runTest {
        val tasks = listOf(
            Task(id = "t1", title = "Project Work", createdAt = 100L, priority = TaskPriority.HIGH),
            Task(id = "t2", title = "Study DSA", createdAt = 100L, priority = TaskPriority.MEDIUM),
        )
        val snapshot = intelligenceEngine.buildSnapshot(
            userProfile = UserProfile("1", "Vijay", "Vijay", "default"),
            buddyMode = BuddyMode.ACTIVE,
            activeTasks = tasks,
            memories = emptyList(),
        )

        val decision = intelligenceEngine.processQuery("What is important today?", snapshot)

        assertEquals(ActionType.ANSWER, decision.actionType)
        assertTrue(decision.primaryTextResponse.contains("Project Work"))
        assertTrue(decision.primaryTextResponse.contains("Study DSA"))
    }

    @Test
    fun `TEST 3 - Plan my day creates schedule strictly from existing tasks`() = runTest {
        val tasks = listOf(
            Task(id = "t1", title = "Morning Yoga", createdAt = 100L, reminderTime = System.currentTimeMillis() + 3600000L),
            Task(id = "t2", title = "Java Practice", createdAt = 100L, reminderTime = System.currentTimeMillis() + 7200000L),
        )
        val snapshot = intelligenceEngine.buildSnapshot(
            userProfile = UserProfile("1", "Vijay", "Vijay", "default"),
            buddyMode = BuddyMode.ACTIVE,
            activeTasks = tasks,
            memories = emptyList(),
        )

        val decision = intelligenceEngine.processQuery("Plan my day.", snapshot)

        assertEquals(ActionType.ANSWER, decision.actionType)
        assertTrue(decision.primaryTextResponse.contains("Morning Yoga"))
        assertTrue(decision.primaryTextResponse.contains("Java Practice"))
    }

    @Test
    fun `TEST 4 - Travel situation detection for Chennai trip`() = runTest {
        val tasks = listOf(Task(id = "t1", title = "Travel to Chennai tomorrow", createdAt = 100L))
        val snapshot = intelligenceEngine.buildSnapshot(
            userProfile = null,
            buddyMode = BuddyMode.ACTIVE,
            activeTasks = tasks,
            memories = emptyList(),
        )

        assertTrue(snapshot.situations.contains(SituationFlag.TRAVEL_DAY))
    }

    @Test
    fun `TEST 5 - Context relevance omits battery and weather for coding explanation`() {
        val snapshot = AIOSContextSnapshot(
            hourOfDay = 14,
            timeSalutation = "Good afternoon",
            userProfile = null,
            batteryLevel = 18,
            weatherCondition = "Rain",
            activeTasks = listOf(Task(id = "t1", title = "Recursion practice", createdAt = 100L)),
            situations = setOf(SituationFlag.NORMAL_DAY),
        )

        val scored = contextScorer.scoreAndFilterContext("Explain recursion", UserIntent.EXPLANATION, snapshot)

        assertFalse(scored.includeBattery, "Coding query must omit battery")
        assertFalse(scored.includeWeather, "Coding query must omit weather")
    }

    @Test
    fun `TEST 6 - Explanation why charge phone uses actual low battery and travel context`() = runTest {
        val snapshot = AIOSContextSnapshot(
            hourOfDay = 20,
            timeSalutation = "Good evening",
            userProfile = null,
            batteryLevel = 18,
            situations = setOf(SituationFlag.TRAVEL_DAY, SituationFlag.LOW_BATTERY),
        )

        val decision = intelligenceEngine.processQuery("Why did you suggest this?", snapshot)

        assertEquals(ActionType.ANSWER, decision.actionType)
        assertTrue(decision.primaryTextResponse.contains("18%"))
        assertTrue(decision.primaryTextResponse.contains("travel"))
    }

    @Test
    fun `TEST 7 - Ambiguous Move that command triggers ASK_CLARIFICATION`() = runTest {
        val tasks = listOf(
            Task(id = "t1", title = "Task One", createdAt = 100L),
            Task(id = "t2", title = "Task Two", createdAt = 100L),
        )
        val snapshot = intelligenceEngine.buildSnapshot(
            userProfile = null,
            buddyMode = BuddyMode.ACTIVE,
            activeTasks = tasks,
            memories = emptyList(),
        )

        val decision = intelligenceEngine.processQuery("Move that to 8", snapshot)

        assertEquals(ActionType.ASK_CLARIFICATION, decision.actionType)
        assertNotNull(decision.clarificationQuestion)
    }

    @Test
    fun `TEST 8 - Proactive notification policy generates single consolidated suggestion`() {
        val snapshot = AIOSContextSnapshot(
            hourOfDay = 21,
            timeSalutation = "Good night",
            userProfile = null,
            batteryLevel = 18,
            weatherCondition = "Rain",
            situations = setOf(SituationFlag.TRAVEL_DAY, SituationFlag.LOW_BATTERY, SituationFlag.RAIN_RISK),
        )

        val suggestion = proactivePolicy.generateConsolidatedProactiveSuggestion(snapshot)

        assertNotNull(suggestion)
        assertTrue(suggestion!!.contains("traveling"))
        assertTrue(suggestion.contains("18%"))
        assertTrue(suggestion.contains("umbrella"))
    }

    @Test
    fun `TEST 9 - Anti-nagging policy rate limits repeated proactive notifications`() {
        val hash = "travel_battery_rain"
        val firstAllowed = proactivePolicy.shouldDeliverProactiveAlert(hash, PriorityLevel.HIGH, BuddyMode.ACTIVE, now = 10000L)
        val secondAllowed = proactivePolicy.shouldDeliverProactiveAlert(hash, PriorityLevel.HIGH, BuddyMode.ACTIVE, now = 10001L)

        assertTrue(firstAllowed, "First notification should be allowed")
        assertFalse(secondAllowed, "Immediate duplicate notification must be suppressed by anti-nagging policy")
    }

    @Test
    fun `TEST 10 - Anti-nagging policy respects user alert dismissal`() {
        val hash = "travel_alert_1"
        proactivePolicy.dismissAlert(hash)

        val allowed = proactivePolicy.shouldDeliverProactiveAlert(hash, PriorityLevel.HIGH, BuddyMode.ACTIVE)

        assertFalse(allowed, "Dismissed alert must never be re-delivered")
    }
}
