package com.sereneflow.timer

import com.sereneflow.timer.model.RoundPosition
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.storage.StorageJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RoundsTest {

    private val steps = listOf(RoutineStep("a", "Work", 40), RoutineStep("b", "Breathe", 20))

    @Test
    fun oneRoundIsTheRoutineItself() {
        val routine = Routine(id = "r", title = "R", steps = steps)
        assertSame(routine, routine.expandedForRounds("Rest"))
        assertEquals(60, routine.totalDurationSeconds)
    }

    @Test
    fun roundsRepeatTheStepsWithARestBetweenButNotAfter() {
        val routine = Routine(id = "r", title = "R", steps = steps, rounds = 3, restBetweenRoundsSeconds = 30)
        val expanded = routine.expandedForRounds("Rest")
        assertEquals(
            listOf("Work", "Breathe", "Rest", "Work", "Breathe", "Rest", "Work", "Breathe"),
            expanded.steps.map { it.title }
        )
        assertEquals(listOf(40, 20, 30, 40, 20, 30, 40, 20), expanded.steps.map { it.durationSeconds })
        val ids = expanded.steps.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Expanded step ids stay unique")
        assertEquals(1, expanded.roundCount)
        assertEquals(3 * 60 + 2 * 30, routine.totalDurationSeconds)
        assertEquals(routine.totalDurationSeconds, expanded.totalDurationSeconds)
    }

    @Test
    fun roundsWithoutRestHaveNoRestSteps() {
        val routine = Routine(id = "r", title = "R", steps = steps, rounds = 2)
        assertEquals(listOf("a_r1", "b_r1", "a_r2", "b_r2"), routine.expandedForRounds("Rest").steps.map { it.id })
    }

    @Test
    fun roundPositionMapsExpandedIndexes() {
        val routine = Routine(id = "r", title = "R", steps = steps, rounds = 3, restBetweenRoundsSeconds = 30)
        assertEquals(RoundPosition(1, 3, 0), routine.roundPositionAt(0))
        assertEquals(RoundPosition(1, 3, 1), routine.roundPositionAt(1))
        assertEquals(RoundPosition(1, 3, null), routine.roundPositionAt(2))
        assertEquals(RoundPosition(2, 3, 0), routine.roundPositionAt(3))
        assertEquals(RoundPosition(3, 3, 1), routine.roundPositionAt(7))
    }

    @Test
    fun outOfRangeRoundsAreClamped() {
        assertEquals(1, Routine(id = "r", title = "R", steps = steps, rounds = 0).roundCount)
        assertEquals(Routine.MAX_ROUNDS, Routine(id = "r", title = "R", steps = steps, rounds = 1000).roundCount)
    }

    @Test
    fun filesWithoutRoundsLoadAsOneRound() {
        val routine = StorageJson.decodeFromString<Routine>("""{"id":"r","title":"R","steps":[]}""")
        assertEquals(1, routine.rounds)
        assertEquals(0, routine.restBetweenRoundsSeconds)
    }
}
