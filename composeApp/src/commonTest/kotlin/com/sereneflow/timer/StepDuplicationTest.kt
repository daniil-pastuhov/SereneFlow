package com.sereneflow.timer

import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.model.newStepId
import com.sereneflow.timer.model.withDuplicatedStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class StepDuplicationTest {

    private val steps = listOf(
        RoutineStep("a", "Breathe", 60, "In and out"),
        RoutineStep("b", "Stretch", 120),
        RoutineStep("c", "Rest", 30)
    )

    @Test
    fun copyIsInsertedRightAfterTheSource() {
        val result = steps.withDuplicatedStep(1, newId = "copy")
        assertEquals(listOf("a", "b", "copy", "c"), result.map { it.id })
        assertEquals(steps[1].copy(id = "copy"), result[2])
    }

    @Test
    fun duplicatingTheLastStepAppends() {
        val result = steps.withDuplicatedStep(2, newId = "copy")
        assertEquals(listOf("a", "b", "c", "copy"), result.map { it.id })
    }

    @Test
    fun copyKeepsTitleDurationAndDescription() {
        val copy = steps.withDuplicatedStep(0, newId = "copy")[1]
        assertEquals("Breathe", copy.title)
        assertEquals(60, copy.durationSeconds)
        assertEquals("In and out", copy.description)
    }

    @Test
    fun outOfRangeIndexChangesNothing() {
        assertSame(steps, steps.withDuplicatedStep(5))
        assertSame(steps, steps.withDuplicatedStep(-1))
    }

    @Test
    fun freshIdsDiffer() {
        val result = steps.withDuplicatedStep(0)
        assertNotEquals(result[0].id, result[1].id)
        assertNotEquals(newStepId(), newStepId())
    }
}
