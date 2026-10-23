package com.sereneflow.timer

import com.sereneflow.timer.audio.AudioController
import com.sereneflow.timer.audio.HapticKind
import com.sereneflow.timer.engine.StepReminder
import com.sereneflow.timer.engine.TimerEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineCues
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.model.VisualEffectType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeAudioController : AudioController {
    val spokenList = mutableListOf<String>()
    val soundsPlayed = mutableListOf<CueSound>()
    var bowlsPlayed = 0
    var chimesPlayed = 0

    override fun playSound(sound: CueSound) {
        if (sound == CueSound.NONE) return
        soundsPlayed.add(sound)
        if (sound == CueSound.TIBETAN_BOWL) bowlsPlayed++
    }

    override fun playTibetanBowl(fundamentalHz: Double, durationMs: Long) {
        bowlsPlayed++
    }

    override fun playZenChime(frequencyHz: Double, durationMs: Long) {
        chimesPlayed++
    }

    override fun playStepComplete() {
        chimesPlayed++
    }

    override fun playRoutineComplete() {
        bowlsPlayed++
    }

    override fun speak(text: String) {
        spokenList.add(text)
    }

    var stopCalls = 0

    override fun stop() {
        stopCalls++
    }

    val haptics = mutableListOf<HapticKind>()

    override fun haptic(kind: HapticKind) {
        haptics.add(kind)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {

    private val testRoutine = Routine(
        id = "test_routine",
        title = "Test Mindful Flow",
        description = "Test Description",
        visualEffect = VisualEffectType.WATER,
        steps = listOf(
            RoutineStep("s1", "Centering Breath", 10),
            RoutineStep("s2", "Presence & Calm", 20)
        )
    )

    private val bowlAndVoice = RoutineCues(VisualEffectType.WATER, CueSound.TIBETAN_BOWL, voice = true)

    @Test
    fun testTimerEngineInitialState() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(routine = testRoutine, cues = bowlAndVoice, audioController = audio, scope = this)
        val state = engine.timerState.value

        assertEquals(0, state.currentStepIndex)
        assertEquals(10, state.secondsRemainingInStep)
        assertEquals("Centering Breath", state.currentStep?.title)
        assertEquals("Presence & Calm", state.nextStep?.title)
        assertEquals("00:10", state.formattedRemaining)
        assertFalse(state.isRunning)
    }

    @Test
    fun testTimerEngineSkipAndPrevious() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(routine = testRoutine, cues = bowlAndVoice, audioController = audio, scope = this)

        engine.skipStep()
        var state = engine.timerState.value
        assertEquals(1, state.currentStepIndex)
        assertEquals(20, state.secondsRemainingInStep)
        assertEquals("Presence & Calm", state.currentStep?.title)

        engine.previousStep()
        state = engine.timerState.value
        assertEquals(0, state.currentStepIndex)
        assertEquals(10, state.secondsRemainingInStep)
    }

    @Test
    fun testTimerEngineTickingAndAudio() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            scope = this,
            clock = { testScheduler.currentTime }
        )

        engine.start()
        assertTrue(engine.timerState.value.isRunning)
        assertTrue(audio.bowlsPlayed >= 1, "Bowl should sound on start")
        assertTrue(audio.spokenList.isNotEmpty(), "Voice guidance should speak step title")

        // Advance 5 seconds (which is half of 10s: 10 - 5 = 5 remaining)
        advanceTimeBy(5100)
        assertEquals(5, engine.timerState.value.secondsRemainingInStep)
        engine.stop()
    }

    @Test
    fun testReminderFiresOnceAtThreshold() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            reminderThresholds = setOf(50),
            scope = this,
            clock = { testScheduler.currentTime }
        )

        engine.start()
        advanceTimeBy(4100) // 6s left of 10s: not yet at 50%
        assertTrue(audio.spokenList.none { it.contains("percent left") })

        advanceTimeBy(1000) // 5s left: 50% reached
        assertEquals(listOf("Centering Breath, 50 percent left"), audio.spokenList.filter { it.contains("percent left") })

        advanceTimeBy(2000) // still in step 1, must not repeat
        assertEquals(1, audio.spokenList.count { it.contains("percent left") })
        engine.stop()
    }

    @Test
    fun testReminderReachesTheScreenWhenMuted() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            reminderThresholds = setOf(50),
            scope = this,
            clock = { testScheduler.currentTime }
        )
        val shown = mutableListOf<StepReminder>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.reminders.toList(shown) }

        engine.setMuted(true)
        engine.start()
        advanceTimeBy(5100) // 5s left of 10s: 50% reached

        assertEquals(listOf(StepReminder("Centering Breath", 50)), shown)
        assertTrue(audio.spokenList.none { it.contains("percent left") }, "Muted: nothing is spoken")
        engine.stop()
    }

    @Test
    fun testRemindersResetForNextStep() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            reminderThresholds = setOf(50),
            scope = this,
            clock = { testScheduler.currentTime }
        )

        engine.start()
        advanceTimeBy(10_100) // step 1 done, step 2 (20s) begins
        assertEquals(1, engine.timerState.value.currentStepIndex)

        advanceTimeBy(10_000) // 10s left of 20s
        assertTrue(audio.spokenList.contains("Presence & Calm, 50 percent left"))
        assertEquals(2, audio.spokenList.count { it.contains("percent left") })
        engine.stop()
    }

    @Test
    fun testMultipleThresholdsAnnouncedInOrder() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            reminderThresholds = setOf(75, 50, 25),
            scope = this,
            clock = { testScheduler.currentTime }
        )

        engine.start()
        advanceTimeBy(3100) // 7s left of 10s: 75% crossed
        advanceTimeBy(5000) // 2s left: 50% then 25% crossed on later ticks
        val reminders = audio.spokenList.filter { it.contains("percent left") }
        assertEquals(
            listOf("Centering Breath, 75 percent left", "Centering Breath, 50 percent left", "Centering Breath, 25 percent left"),
            reminders
        )
        engine.stop()
    }

    @Test
    fun testShortStepsSkipReminders() = runTest {
        val audio = FakeAudioController()
        val shortRoutine = testRoutine.copy(steps = listOf(RoutineStep("q", "Quick", 8)))
        val engine = TimerEngine(
            routine = shortRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            reminderThresholds = setOf(50),
            scope = this,
            clock = { testScheduler.currentTime }
        )

        engine.start()
        advanceTimeBy(5100)
        assertTrue(audio.spokenList.none { it.contains("percent left") })
        engine.stop()
    }

    @Test
    fun testMuteSuppressesAllAudio() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            reminderThresholds = setOf(50),
            scope = this,
            clock = { testScheduler.currentTime }
        )

        engine.setMuted(true)
        assertTrue(engine.timerState.value.isMuted)
        engine.start()
        advanceTimeBy(9100) // crosses 50% and the 3-2-1 countdown
        engine.skipStep()

        assertTrue(audio.spokenList.isEmpty(), "Muted engine must not speak")
        assertEquals(0, audio.bowlsPlayed)
        assertEquals(0, audio.chimesPlayed)
        assertTrue(audio.stopCalls >= 1, "Muting should stop speech in progress")

        engine.toggleMute()
        assertFalse(engine.timerState.value.isMuted)
        engine.stop()
    }

    @Test
    fun testCatchesUpAfterSuspension() = runTest {
        val audio = FakeAudioController()
        var now = 0L
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            scope = this,
            clock = { now }
        )

        engine.start()
        audio.spokenList.clear()

        // Process frozen for 25s (app suspended): no ticks run, but wall-clock time moves on.
        now = 25_000
        advanceTimeBy(300) // first tick after waking up

        val state = engine.timerState.value
        assertEquals(1, state.currentStepIndex, "Should have moved past the 10s first step")
        assertEquals(5, state.secondsRemainingInStep, "15s into the 20s second step")
        assertEquals(listOf("Presence & Calm, 20 seconds"), audio.spokenList, "Announce only the step we landed on")

        now = 31_000
        advanceTimeBy(300)
        assertTrue(engine.timerState.value.isCompleted, "Routine ended while suspended")
        engine.stop()
    }

    @Test
    fun testDurationsAreSpokenInWords() {
        fun spoken(seconds: Int) = RoutineStep("s", "Step", seconds).spokenDuration
        assertEquals("2 minutes", spoken(120))
        assertEquals("1 minute", spoken(60))
        assertEquals("1 minute 30 seconds", spoken(90))
        assertEquals("2 minutes 1 second", spoken(121))
        assertEquals("45 seconds", spoken(45))
        assertEquals("1 second", spoken(1))
    }

    @Test
    fun testFastForwardMovesThroughSteps() = runTest {
        val audio = FakeAudioController()
        val now = 0L
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            scope = this,
            clock = { now }
        )

        engine.fastForward(4_000) // not started: nothing to move
        assertEquals(10_000, engine.timerState.value.pausedRemainingMs)

        engine.start()
        engine.fastForward(4_000)
        assertEquals(0, engine.timerState.value.currentStepIndex)
        assertEquals(6, engine.timerState.value.secondsRemainingInStep)
        assertEquals(0.4f, engine.timerState.value.stepProgressAt(now), 0.0001f)

        engine.fastForward(8_000)
        assertEquals(1, engine.timerState.value.currentStepIndex)
        assertEquals(18, engine.timerState.value.secondsRemainingInStep)
        engine.stop()
    }

    @Test
    fun testPauseKeepsRemainingTime() = runTest {
        val audio = FakeAudioController()
        var now = 0L
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            scope = this,
            clock = { now }
        )

        engine.start()
        now = 3_400
        advanceTimeBy(300)
        engine.pause()
        assertEquals(6_600, engine.timerState.value.pausedRemainingMs)
        assertEquals(null, engine.timerState.value.stepEndEpochMs)

        now = 60_000 // a long pause must not consume step time
        advanceTimeBy(1_000)
        engine.resume()
        assertEquals(66_600, engine.timerState.value.stepEndEpochMs)
        assertEquals(0, engine.timerState.value.currentStepIndex)
        engine.stop()
    }

    @Test
    fun testPauseAtStepBoundary() = runTest {
        val audio = FakeAudioController()
        var now = 0L
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            scope = this,
            clock = { now }
        )

        engine.start()
        // Paused exactly as step 1 ends, before a tick has seen it: the pause lands on step 2, whole.
        now = 10_000
        engine.pause()
        var state = engine.timerState.value
        assertEquals(1, state.currentStepIndex)
        assertTrue(state.isPaused)
        assertEquals(20_000, state.pausedRemainingMs)
        assertEquals(20, state.secondsRemainingInStep)

        now = 50_000
        engine.resume()
        assertEquals(70_000, engine.timerState.value.stepEndEpochMs)

        // Paused exactly as the last step ends: the routine completes rather than pausing.
        now = 70_000
        engine.pause()
        state = engine.timerState.value
        assertTrue(state.isCompleted)
        assertFalse(state.isPaused)
        assertFalse(state.isRunning)
        engine.stop()
    }

    @Test
    fun testVoiceWithoutSoundOnlySpeaks() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(
            routine = testRoutine,
            cues = RoutineCues(VisualEffectType.WATER, CueSound.NONE, voice = true),
            audioController = audio,
            reminderThresholds = setOf(50),
            scope = this,
            clock = { testScheduler.currentTime }
        )

        engine.start()
        assertEquals(listOf("Step 1: Centering Breath, 10 seconds"), audio.spokenList)
        advanceTimeBy(5100)
        assertTrue(audio.spokenList.any { it.contains("50 percent left") })
        advanceTimeBy(25_000)
        assertTrue(engine.timerState.value.isCompleted)
        assertTrue(audio.soundsPlayed.isEmpty())
        assertEquals(0, audio.bowlsPlayed, "No tone, not even at the end")
        assertEquals("Routine complete. Well done.", audio.spokenList.last())
        engine.stop()
    }

    @Test
    fun testSoundWithoutVoiceNeverSpeaks() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(
            routine = testRoutine,
            cues = RoutineCues(VisualEffectType.WATER, CueSound.KALIMBA, voice = false),
            audioController = audio,
            reminderThresholds = setOf(50),
            scope = this,
            clock = { testScheduler.currentTime }
        )

        engine.start()
        advanceTimeBy(31_000)
        assertTrue(engine.timerState.value.isCompleted)
        assertEquals(listOf(CueSound.KALIMBA, CueSound.KALIMBA), audio.soundsPlayed)
        assertTrue(audio.spokenList.isEmpty())
        assertTrue(audio.chimesPlayed > 0, "Reminder and countdown chimes still play")
        engine.stop()
    }

    @Test
    fun testSilentRoutinePlaysNothing() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(
            routine = testRoutine,
            cues = RoutineCues(VisualEffectType.WATER, CueSound.NONE, voice = false),
            audioController = audio,
            reminderThresholds = setOf(50),
            scope = this,
            clock = { testScheduler.currentTime }
        )

        engine.start()
        advanceTimeBy(31_000)
        assertTrue(engine.timerState.value.isCompleted)
        assertTrue(audio.soundsPlayed.isEmpty())
        assertTrue(audio.spokenList.isEmpty())
        assertEquals(0, audio.chimesPlayed)
        assertEquals(0, audio.bowlsPlayed)
        engine.stop()
    }

    @Test
    fun testHapticsMarkStepsCountdownAndCompletionEvenWhenMuted() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            haptics = true,
            scope = this,
            clock = { testScheduler.currentTime }
        )

        engine.setMuted(true)
        engine.start()
        advanceTimeBy(31_000)
        assertTrue(engine.timerState.value.isCompleted)
        val tick = HapticKind.COUNTDOWN_TICK
        assertEquals(
            listOf(HapticKind.STEP_START, tick, tick, tick, HapticKind.STEP_START, tick, tick, tick, HapticKind.ROUTINE_COMPLETE),
            audio.haptics
        )
        assertTrue(audio.spokenList.isEmpty(), "Muted: no voice")
        engine.stop()
    }

    @Test
    fun testExtendStepMovesTheRunningStepEnd() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            scope = this,
            clock = { testScheduler.currentTime }
        )
        engine.start()
        advanceTimeBy(6_100) // 4s left of the 10s step
        engine.extendStep(30_000)
        var state = engine.timerState.value
        assertEquals(34, state.secondsRemainingInStep)
        assertEquals(40_000, state.currentStepDurationMs)
        assertEquals(0.15f, state.stepProgressAt(testScheduler.currentTime), 0.01f)

        advanceTimeBy(30_000) // still in step 1 thanks to the extra time
        assertEquals(0, engine.timerState.value.currentStepIndex)
        advanceTimeBy(4_000)
        state = engine.timerState.value
        assertEquals(1, state.currentStepIndex)
        assertEquals(0, state.stepExtensionMs, "The next step starts without the extension")
        assertEquals(20, state.secondsRemainingInStep)
        engine.stop()
    }

    @Test
    fun testExtendStepWhilePausedAddsRemainingTime() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            scope = this,
            clock = { testScheduler.currentTime }
        )
        engine.start()
        advanceTimeBy(2_100)
        engine.pause()
        engine.extendStep(30_000)
        assertEquals(38, engine.timerState.value.secondsRemainingInStep)

        engine.resume()
        advanceTimeBy(37_000)
        assertEquals(0, engine.timerState.value.currentStepIndex)
        advanceTimeBy(1_500)
        assertEquals(1, engine.timerState.value.currentStepIndex)
        engine.stop()
    }

    @Test
    fun testExtendStepAfterCompletionDoesNothing() = runTest {
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = FakeAudioController(),
            scope = this,
            clock = { testScheduler.currentTime }
        )
        engine.start()
        advanceTimeBy(31_000)
        engine.extendStep(30_000)
        val state = engine.timerState.value
        assertTrue(state.isCompleted)
        assertEquals(0, state.secondsRemainingInStep)
        assertEquals(0, state.stepExtensionMs)
    }

    @Test
    fun testNoHapticsWhenOff() = runTest {
        val audio = FakeAudioController()
        val engine = TimerEngine(
            routine = testRoutine,
            cues = bowlAndVoice,
            audioController = audio,
            scope = this,
            clock = { testScheduler.currentTime }
        )
        engine.start()
        advanceTimeBy(31_000)
        assertTrue(audio.haptics.isEmpty())
        engine.stop()
    }
}
