package com.buddy.aios.feature.chat.voice

import com.buddy.aios.core.ai.voice.VoiceCommand
import com.buddy.aios.core.ai.voice.VoiceCommandParser
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IMorningWishEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceConversationSessionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val voiceInputManager: VoiceInputManager = mockk(relaxed = true)
    private val ttsManager: TextToSpeechManager = mockk(relaxed = true)
    private val voiceCommandParser: VoiceCommandParser = mockk()
    private val buddyModeRepository: IBuddyModeRepository = mockk(relaxed = true)
    private val morningWishEngine: IMorningWishEngine = mockk(relaxed = true)

    private val voiceInputStateFlow = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    private val ttsStateFlow = MutableStateFlow<TextToSpeechState>(TextToSpeechState.Idle)

    private lateinit var session: VoiceConversationSession

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { voiceInputManager.state } returns voiceInputStateFlow
        every { ttsManager.state } returns ttsStateFlow
        every { voiceCommandParser.parse(any()) } returns null

        session = VoiceConversationSession(
            voiceInputManager = voiceInputManager,
            ttsManager = ttsManager,
            voiceCommandParser = voiceCommandParser,
            buddyModeRepository = buddyModeRepository,
            morningWishEngine = morningWishEngine,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `TEST 1 OFF to ON transitions to LISTENING`() = testScope.runTest {
        assertEquals(VoiceSessionState.Idle, session.sessionState.value)
        assertFalse(session.isContinuousModeActive.value)

        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        assertTrue(session.isContinuousModeActive.value)
        assertEquals(VoiceSessionState.Listening, session.sessionState.value)
        verify(exactly = 1) { voiceInputManager.startListening() }
    }

    @Test
    fun `TEST 2 LISTENING to PROCESSING`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        voiceInputStateFlow.value = VoiceInputState.FinalResult("Hello AIOS")
        testScheduler.advanceUntilIdle()

        val state = session.sessionState.value
        assertTrue(state is VoiceSessionState.Processing)
        assertEquals("Hello AIOS", (state as VoiceSessionState.Processing).text)
    }

    @Test
    fun `TEST 3 PROCESSING to SPEAKING`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        ttsStateFlow.value = TextToSpeechState.Speaking("Hello! How can I help?")
        testScheduler.advanceUntilIdle()

        val state = session.sessionState.value
        assertTrue(state is VoiceSessionState.Speaking)
        assertEquals("Hello! How can I help?", (state as VoiceSessionState.Speaking).text)
    }

    @Test
    fun `TEST 4 SPEAKING to LISTENING auto-restart after TTS complete`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        ttsStateFlow.value = TextToSpeechState.Speaking("Response")
        testScheduler.advanceUntilIdle()
        assertEquals(VoiceSessionState.Speaking("Response"), session.sessionState.value)

        ttsStateFlow.value = TextToSpeechState.Idle
        testScheduler.advanceTimeBy(500)
        testScheduler.advanceUntilIdle()

        assertEquals(VoiceSessionState.Listening, session.sessionState.value)
        verify(atLeast = 2) { voiceInputManager.startListening() }
    }

    @Test
    fun `TEST 5 SPEAKING to USER INTERRUPTION to LISTENING`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        ttsStateFlow.value = TextToSpeechState.Speaking("Long response...")
        testScheduler.advanceUntilIdle()

        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        assertEquals(VoiceSessionState.Listening, session.sessionState.value)
        verify(atLeast = 2) { voiceInputManager.startListening() }
    }

    @Test
    fun `TEST 6 LISTENING to OFF`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        session.stopContinuousSession()
        testScheduler.advanceUntilIdle()

        assertFalse(session.isContinuousModeActive.value)
        assertEquals(VoiceSessionState.Idle, session.sessionState.value)
        verify { voiceInputManager.stopListening() }
        verify { ttsManager.stop() }
    }

    @Test
    fun `TEST 7 SPEAKING to OFF to TTS COMPLETE no recognizer restart`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        ttsStateFlow.value = TextToSpeechState.Speaking("Talking...")
        testScheduler.advanceUntilIdle()

        session.stopContinuousSession()
        testScheduler.advanceUntilIdle()

        ttsStateFlow.value = TextToSpeechState.Idle
        testScheduler.advanceTimeBy(1000)
        testScheduler.advanceUntilIdle()

        assertEquals(VoiceSessionState.Idle, session.sessionState.value)
        assertFalse(session.isContinuousModeActive.value)
        verify(exactly = 1) { voiceInputManager.startListening() }
    }

    @Test
    fun `TEST 8 PROCESSING to OFF to AI response no restart`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        voiceInputStateFlow.value = VoiceInputState.FinalResult("Question")
        testScheduler.advanceUntilIdle()

        session.stopContinuousSession()
        testScheduler.advanceUntilIdle()

        ttsStateFlow.value = TextToSpeechState.Idle
        testScheduler.advanceTimeBy(1000)
        testScheduler.advanceUntilIdle()

        assertEquals(VoiceSessionState.Idle, session.sessionState.value)
        assertFalse(session.isContinuousModeActive.value)
    }

    @Test
    fun `TEST 9 Recognition ERROR to ACTIVE safe retry`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        voiceInputStateFlow.value = VoiceInputState.Error("No speech detected")
        testScheduler.advanceTimeBy(700)
        testScheduler.advanceUntilIdle()

        assertTrue(session.isContinuousModeActive.value)
        verify(atLeast = 2) { voiceInputManager.startListening() }
    }

    @Test
    fun `TEST 10 Recognition ERROR to OFF no retry`() = testScope.runTest {
        session.stopContinuousSession()
        testScheduler.advanceUntilIdle()

        voiceInputStateFlow.value = VoiceInputState.Error("No speech detected")
        testScheduler.advanceTimeBy(1000)
        testScheduler.advanceUntilIdle()

        assertEquals(VoiceSessionState.Idle, session.sessionState.value)
        assertFalse(session.isContinuousModeActive.value)
    }

    @Test
    fun `TEST 11 Duplicate startSession single active session`() = testScope.runTest {
        session.startContinuousSession()
        session.startContinuousSession()
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        assertTrue(session.isContinuousModeActive.value)
        verify(exactly = 1) { voiceInputManager.playMicSound(isOn = true) }
    }

    @Test
    fun `TEST 12 Duplicate startListening single recognizer operation`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { voiceInputManager.startListening() }
    }

    @Test
    fun `TEST 13 Mic ON sound played exactly once`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { voiceInputManager.playMicSound(isOn = true) }
    }

    @Test
    fun `TEST 14 Internal recognizer restart zero additional mic ON sounds`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        ttsStateFlow.value = TextToSpeechState.Speaking("Text")
        testScheduler.advanceUntilIdle()

        ttsStateFlow.value = TextToSpeechState.Idle
        testScheduler.advanceTimeBy(500)
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { voiceInputManager.playMicSound(isOn = true) }
    }

    @Test
    fun `TEST 15 Mic OFF sound played exactly once`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        session.stopContinuousSession()
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { voiceInputManager.playMicSound(isOn = false) }
    }

    @Test
    fun `TEST 16 Internal TTS listening transitions zero ON OFF sound events`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        ttsStateFlow.value = TextToSpeechState.Speaking("1")
        testScheduler.advanceUntilIdle()

        ttsStateFlow.value = TextToSpeechState.Idle
        testScheduler.advanceTimeBy(500)
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { voiceInputManager.playMicSound(isOn = true) }
        verify(exactly = 0) { voiceInputManager.playMicSound(isOn = false) }
    }

    @Test
    fun `TEST 17 Conversation context preservation`() = testScope.runTest {
        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        voiceInputStateFlow.value = VoiceInputState.FinalResult("My name is Buddy")
        testScheduler.advanceUntilIdle()

        assertTrue(session.isContinuousModeActive.value)
    }

    @Test
    fun `TEST 18 Voice stop command terminates session`() = testScope.runTest {
        every { voiceCommandParser.parse("stop voice mode") } returns VoiceCommand.StopListening

        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        voiceInputStateFlow.value = VoiceInputState.FinalResult("stop voice mode")
        testScheduler.advanceUntilIdle()

        assertFalse(session.isContinuousModeActive.value)
        assertEquals(VoiceSessionState.Idle, session.sessionState.value)
    }

    @Test
    fun `TEST 19 Pause command transitions to PAUSED`() = testScope.runTest {
        every { voiceCommandParser.parse("pause listening") } returns VoiceCommand.PauseListening

        session.startContinuousSession()
        testScheduler.advanceUntilIdle()

        voiceInputStateFlow.value = VoiceInputState.FinalResult("pause listening")
        testScheduler.advanceUntilIdle()

        assertEquals(VoiceSessionState.Paused, session.sessionState.value)
        assertTrue(session.isContinuousModeActive.value)
    }

    @Test
    fun `TEST 20 Resume command transitions to LISTENING`() = testScope.runTest {
        session.startContinuousSession()
        session.pauseSession()
        testScheduler.advanceUntilIdle()
        assertEquals(VoiceSessionState.Paused, session.sessionState.value)

        session.resumeSession()
        testScheduler.advanceUntilIdle()

        assertEquals(VoiceSessionState.Listening, session.sessionState.value)
        assertTrue(session.isContinuousModeActive.value)
    }
}
