package com.sereneflow.timer

import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.platform.MAX_QUICK_START_ITEMS
import com.sereneflow.timer.platform.quickStartItems
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun otherLinksAreIgnored() {
        assertNull(ExternalAction.parseUrl("https://example.com/start?routine=a"))
        assertNull(ExternalAction.parseUrl("sereneflow://delete?routine=a"))
    }

    @Test
    fun lastStartedRoutineComesFirstAndListIsCapped() {
        val routines = (1..6).map { Routine(id = "r$it", title = "R$it", steps = listOf(RoutineStep("s", "S", 60))) } +
            Routine(id = "empty", title = "Empty")
        val items = quickStartItems(routines, lastRoutineId = "r5")
        assertEquals(listOf("r5", "r1", "r2", "r3"), items.map { it.routineId })
        assertEquals(MAX_QUICK_START_ITEMS, items.size)
        assertEquals(listOf("r1", "r2"), quickStartItems(routines.take(2) + routines.last(), null).map { it.routineId })
    }
}
