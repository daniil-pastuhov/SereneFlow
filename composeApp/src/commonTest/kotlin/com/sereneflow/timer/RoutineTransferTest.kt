package com.sereneflow.timer

import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineAccent
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.model.VoiceGuidance
import com.sereneflow.timer.repository.RoutineRepository
import com.sereneflow.timer.transfer.RoutineTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineTransferTest {

    private val routine = Routine(
        id = "mine",
        title = "Evening Flow",
        description = "Slow down",
        visualEffect = VisualEffectType.BREATHING_LOTUS,
        sound = CueSound.KALIMBA,
        voice = VoiceGuidance.OFF,
        accentColorHex = RoutineAccent.LAVENDER.argb,
        rounds = 3,
        restBetweenRoundsSeconds = 30,
        steps = listOf(RoutineStep("s1", "Breathe", 60, "Slowly"), RoutineStep("s2", "Rest", 30))
    )

    private fun TestScope.repo(): RoutineRepository = RoutineRepository(
        FakeFileStorage(),
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        TestSeedRoutines
    )

    @Test
    fun exportThenDecodeRoundTrips() {
        val decoded = RoutineTransfer.decode(RoutineTransfer.encode(listOf(routine, routine.copy(id = "b"))))
        assertEquals(listOf(routine, routine.copy(id = "b")), decoded)
    }

    @Test
    fun importAddsCopiesWithFreshIdsAtTheEnd() = runTest {
        val repo = repo()
        val existing = repo.routines.value.single()
        val imported = repo.import(RoutineTransfer.encode(listOf(routine)))

        assertEquals(2, repo.routines.value.size)
        assertEquals(existing, repo.routines.value.first(), "Nothing is replaced or moved")
        assertEquals(imported.single(), repo.routines.value.last())
        val copy = imported.single()
        assertNotEquals(routine.id, copy.id)
        assertTrue(copy.steps.none { step -> routine.steps.any { it.id == step.id } })
        assertEquals(routine.copy(id = copy.id, steps = routine.steps.mapIndexed { i, s -> s.copy(id = copy.steps[i].id) }), copy)
    }

    @Test
    fun importingTwiceGivesTwoRoutines() = runTest {
        val repo = repo()
        val file = RoutineTransfer.encode(listOf(routine))
        val first = repo.import(file).single()
        val second = repo.import(file).single()
        assertNotEquals(first.id, second.id)
        assertEquals(3, repo.routines.value.size)
    }

    @Test
    fun backupKeepsOrder() = runTest {
        val repo = repo()
        val backup = RoutineTransfer.encode(listOf(routine.copy(title = "A"), routine.copy(title = "B")))
        repo.import(backup)
        assertEquals(listOf("Morning Awakening", "A", "B"), repo.routines.value.map { it.title })
    }

    @Test
    fun bareRoutineAndListAreAccepted() {
        val single = """{"id":"x","title":"Solo","steps":[{"id":"s","title":"S","durationSeconds":30}]}"""
        assertEquals("Solo", RoutineTransfer.decode(single).single().title)
        assertEquals(listOf("Solo", "Solo"), RoutineTransfer.decode("[$single,$single]").map { it.title })
    }

    @Test
    fun olderFormatsAreMigratedAndDurationsKeptInRange() {
        val old = """{"routines":[{"id":"x","title":"Old","soundMode":"VOICE_ONLY","accentColorHex":${0xFF5B8A72L},
            "steps":[{"id":"s","title":"S","durationSeconds":1}]}]}"""
        val decoded = RoutineTransfer.decode(old).single()
        assertEquals(CueSound.NONE, decoded.sound)
        assertEquals(VoiceGuidance.ON, decoded.voice)
        assertEquals(null, decoded.accentColorHex)
        assertEquals(10, decoded.steps.single().durationSeconds)
    }

    @Test
    fun junkIsRejected() = runTest {
        assertFailsWith<IllegalArgumentException> { RoutineTransfer.decode("not json") }
        assertFailsWith<IllegalArgumentException> { RoutineTransfer.decode("""{"routines":[]}""") }
        assertFailsWith<IllegalArgumentException> { RoutineTransfer.decode("42") }
        assertFailsWith<IllegalArgumentException> { RoutineTransfer.decode("""{"hello":"world"}""") }
        val repo = repo()
        assertFailsWith<IllegalArgumentException> { repo.import("nope") }
        assertEquals(1, repo.routines.value.size)
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("Evening Flow.sereneflow", RoutineTransfer.fileNameFor(routine, "Routine"))
        assertEquals("Routine.sereneflow", RoutineTransfer.fileNameFor(routine.copy(title = "///"), "Routine"))
    }
}
