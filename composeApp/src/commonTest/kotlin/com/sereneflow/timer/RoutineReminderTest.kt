package com.sereneflow.timer

import com.sereneflow.timer.model.ReminderSchedule
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.platform.RoutineReminder
import com.sereneflow.timer.platform.routineReminders
import com.sereneflow.timer.repository.RoutineRepository
import com.sereneflow.timer.text.weekStartingOn
import com.sereneflow.timer.transfer.RoutineTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineReminderTest {
    private val berlin = TimeZone.of("Europe/Berlin")

    private fun at(text: String, zone: TimeZone = berlin): Long =
        LocalDateTime.parse(text).toInstant(zone).toEpochMilliseconds()

    @Test
    fun nextIsLaterTodayUntilThenTomorrow() {
        val time = ReminderSchedule(7, 30)
        assertEquals(at("2026-03-10T07:30"), time.nextAfter(at("2026-03-10T06:00"), berlin))
        assertEquals(at("2026-03-11T07:30"), time.nextAfter(at("2026-03-10T07:30"), berlin), "Strictly after")
        assertEquals(at("2026-03-11T07:30"), time.nextAfter(at("2026-03-10T21:00"), berlin))
    }

    @Test
    fun followsTheWallClockAcrossDaylightSaving() {
        // Berlin moves to summer time on 29 March 2026: the day is 23 hours long.
        val time = ReminderSchedule(8, 0)
        val first = checkNotNull(time.nextAfter(at("2026-03-28T09:00"), berlin))
        assertEquals(at("2026-03-29T08:00"), first)
        assertEquals(23 * 60 * 60 * 1000L, first - at("2026-03-28T08:00"))
    }

    @Test
    fun aTimeTheClockSkipsStillComes() {
        // 02:30 doesn't exist on 29 March 2026 in Berlin.
        val next = ReminderSchedule(2, 30).nextAfter(at("2026-03-29T00:00"), berlin)
        assertEquals(at("2026-03-29T03:30"), next)
    }

    @Test
    fun onlyTheChosenDaysRemind() {
        // 10 March 2026 is a Tuesday.
        val weekdays = ReminderSchedule(7, 0, ReminderSchedule.WEEKDAYS)
        assertEquals(at("2026-03-13T07:00"), weekdays.nextAfter(at("2026-03-13T06:00"), berlin), "Friday")
        assertEquals(at("2026-03-16T07:00"), weekdays.nextAfter(at("2026-03-13T07:00"), berlin), "Then Monday")

        val sundays = ReminderSchedule(7, 0, setOf(7))
        assertEquals(at("2026-03-15T07:00"), sundays.nextAfter(at("2026-03-10T12:00"), berlin))
        assertEquals(at("2026-03-22T07:00"), sundays.nextAfter(at("2026-03-15T07:00"), berlin), "A week on")
    }

    @Test
    fun noDaysNeverRemind() {
        assertNull(ReminderSchedule(7, 0, emptySet()).nextAfter(at("2026-03-10T06:00"), berlin))
        assertNull(ReminderSchedule(7, 0, setOf(0, 8)).nextAfter(at("2026-03-10T06:00"), berlin))
        assertEquals(emptyList(), routineReminders(listOf(Routine("a", "A", reminder = ReminderSchedule(7, 0, emptySet())))))
    }

    @Test
    fun weeksStartWhereTheUserLives() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), weekStartingOn(1))
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), weekStartingOn(7))
        assertEquals(listOf(6, 7, 1, 2, 3, 4, 5), weekStartingOn(6))
    }

    @Test
    fun outOfRangeTimesAreKeptToTheDay() {
        assertEquals(ReminderSchedule(23, 0), ReminderSchedule(27, -5).normalized())
        assertEquals(ReminderSchedule(0, 59), ReminderSchedule(-1, 99).normalized())
    }

    @Test
    fun onlyRoutinesWithAReminderAreScheduled() {
        val routines = listOf(
            Routine("a", "A", reminder = ReminderSchedule(6, 45)),
            Routine("b", "B"),
            Routine("c", "C", reminder = ReminderSchedule(25, 0))
        )
        assertEquals(
            listOf(RoutineReminder("a", "A", ReminderSchedule(6, 45)), RoutineReminder("c", "C", ReminderSchedule(23, 0))),
            routineReminders(routines)
        )
    }

    @Test
    fun copiesAndImportsDoNotRemind() = runTest {
        val repo = RoutineRepository(
            FakeFileStorage(),
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            TestSeedRoutines
        )
        val original = Routine("r", "Morning", steps = listOf(RoutineStep("s", "Step", 30)), reminder = ReminderSchedule(7, 0))
        repo.saveRoutine(original)

        assertNull(repo.duplicateRoutine(original, "Morning (Copy)").reminder)
        assertNull(repo.import(RoutineTransfer.encode(listOf(original))).single().reminder)
        assertEquals(ReminderSchedule(7, 0), repo.getRoutine("r")?.reminder, "The original keeps its reminder")
    }
}
