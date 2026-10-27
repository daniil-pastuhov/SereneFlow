package com.sereneflow.timer

import com.sereneflow.timer.engine.TimerEngine
import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineCues
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.session.launchCompletionCount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Only routines run to their end count towards the coffee banner, each once. */
@OptIn(ExperimentalCoroutinesApi::class)
class CompletionCountTest {

    private val routine = Routine(
        id = "count_routine",
        title = "Count",
        description = "",
        visualEffect = VisualEffectType.WATER,
        steps = listOf(RoutineStep("s1", "One", 10), RoutineStep("s2", "Two", 20))
    )

    private fun TestScope.engine(clock: () -> Long = { testScheduler.currentTime }) = TimerEngine(
        routine = routine,
        cues = RoutineCues(VisualEffectType.WATER, CueSound.NONE, voice = false),
        audioController = FakeAudioController(),
        phrases = EnglishPhrases,
        scope = this,
        clock = clock
    )

    @Test
    fun aCompletedRoutineCountsOnce() = runTest {
        val engine = engine()
        var count = 0
        backgroundScope.launchCompletionCount(engine, isCurrent = { true }) { count++ }
        engine.start()

        advanceTimeBy(31_000)
        runCurrent()
        assertEquals(1, count)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, count, "Counted once, not on every later state")
        engine.stop()
    }

    @Test
    fun aRoutineEndedEarlyDoesNotCount() = runTest {
        val engine = engine()
        var count = 0
        val job = backgroundScope.launchCompletionCount(engine, isCurrent = { true }) { count++ }
        engine.start()

        advanceTimeBy(12_000)
        job.cancel() // what TimerSession.stop does
        engine.stop()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(0, count)
    }

    @Test
    fun aSessionNoLongerCurrentDoesNotCount() = runTest {
        val engine = engine()
        var count = 0
        backgroundScope.launchCompletionCount(engine, isCurrent = { false }) { count++ }
        engine.start()

        advanceTimeBy(31_000)
        runCurrent()
        assertEquals(0, count)
        engine.stop()
    }

    @Test
    fun aRoutineThatEndedWhileSuspendedCountsOnceItCatchesUp() = runTest {
        var now = 0L
        val engine = engine { now }
        var count = 0
        backgroundScope.launchCompletionCount(engine, isCurrent = { true }) { count++ }
        engine.start()

        // Process frozen while the whole routine's time passes; the first tick after waking catches up.
        now = 45_000
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, count)
        engine.stop()
    }
}
