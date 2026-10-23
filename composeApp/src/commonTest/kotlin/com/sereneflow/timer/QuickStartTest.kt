package com.sereneflow.timer

import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.platform.MAX_QUICK_START_ITEMS
import com.sereneflow.timer.platform.quickStartRoutines
import com.sereneflow.timer.session.TimerSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class QuickStartTest {

    @Test
    fun startUrlRoundTrips() {
        listOf("routine_123", "morning_awakening", "with space/and?odd&chars", "ünïcode").forEach { id ->
            assertEquals(ExternalAction.StartRoutine(id), ExternalAction.parseUrl(ExternalAction.startUrl(id)))
        }
    }

    @Test
    fun linkWithoutRoutineMeansTheLastOne() {
        assertEquals(ExternalAction.StartRoutine(ExternalAction.LAST_ROUTINE), ExternalAction.parseUrl("sereneflow://start"))
        assertEquals(ExternalAction.StartRoutine("abc"), ExternalAction.parseUrl("sereneflow://start?x=1&routine=abc"))
    }

    @Test
    fun assistantNamesFindRoutinesByTitle() {
        val routines = listOf(
            Routine(id = "a", title = "Morning Awakening"),
            Routine(id = "b", title = "Wind-down")
        )
        assertEquals(ExternalAction.StartRoutine("wind-down"), ExternalAction.parseUrl("sereneflow://start?name=wind-down"))
        assertEquals("b", findRoutine(routines, "wind-down")?.id)
        assertEquals("a", findRoutine(routines, "a")?.id)
        assertEquals("a", findRoutine(routines, "morning")?.id)
        assertNull(findRoutine(routines, "evening"))
    }

    @Test
    fun controlLinksRoundTrip() {
        TimerCommand.entries.forEach { command ->
            assertEquals(ExternalAction.ControlTimer(command), ExternalAction.parseUrl(ExternalAction.controlUrl(command)))
        }
        assertEquals("sereneflow://pause", ExternalAction.controlUrl(TimerCommand.PAUSE))
    }

    @Test
    fun controlLinksIgnoreCaseAndParameters() {
        // Assistant may add the exercise it heard; there is only one running routine to act on.
        assertEquals(ExternalAction.ControlTimer(TimerCommand.SKIP), ExternalAction.parseUrl("SereneFlow://Skip"))
        assertEquals(ExternalAction.ControlTimer(TimerCommand.RESUME), ExternalAction.parseUrl("sereneflow://resume?name=Yoga"))
        assertEquals(ExternalAction.ControlTimer(TimerCommand.END), ExternalAction.parseUrl("sereneflow://end/"))
    }

    @Test
    fun controlWithoutARunningRoutineDoesNothing() {
        TimerCommand.entries.forEach { assertFalse(TimerSession.control(it)) }
        // Not queued for the UI either, so it can't act on a routine started later.
        ExternalActions.post(ExternalAction.ControlTimer(TimerCommand.END))
        assertNull(ExternalActions.pending.value)
    }

    @Test
    fun otherLinksAreIgnored() {
        assertNull(ExternalAction.parseUrl("https://example.com/start?routine=a"))
        assertNull(ExternalAction.parseUrl("sereneflow://delete?routine=a"))
        assertNull(ExternalAction.parseUrl("sereneflow://starting"))
        assertNull(ExternalAction.parseUrl("sereneflow://pauses"))
        assertNull(ExternalAction.parseUrl("sereneflow://"))
    }

    @Test
    fun lastStartedRoutineComesFirstAndListIsCapped() {
        val routines = (1..6).map { Routine(id = "r$it", title = "R$it", steps = listOf(RoutineStep("s", "S", 60))) } +
            Routine(id = "empty", title = "Empty")
        val items = quickStartRoutines(routines, lastRoutineId = "r5")
        assertEquals(listOf("r5", "r1", "r2", "r3"), items.map { it.id })
        assertEquals(MAX_QUICK_START_ITEMS, items.size)
        assertEquals(listOf("r1", "r2"), quickStartRoutines(routines.take(2) + routines.last(), null).map { it.id })
    }
}
