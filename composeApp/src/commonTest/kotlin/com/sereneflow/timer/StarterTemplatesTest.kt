package com.sereneflow.timer

import com.sereneflow.timer.repository.RoutineRepository
import com.sereneflow.timer.repository.StarterTemplates
import com.sereneflow.timer.ui.platform.MIN_STEP_SECONDS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StarterTemplatesTest {

    // Host tests can't read resources; the resource names stand in for the words.
    private val templates = StarterTemplates.all.map { template -> template.toRoutine { it.key } }

    @Test
    fun templatesAreUsableRoutines() {
        assertTrue(templates.size >= 5)
        templates.forEach { template ->
            assertTrue(template.steps.isNotEmpty(), template.title)
            assertTrue(template.steps.all { it.durationSeconds >= MIN_STEP_SECONDS }, template.title)
        }
    }

    @Test
    fun eachUseGetsFreshIds() {
        val template = templates.first()
        val a = StarterTemplates.instantiate(template)
        val b = StarterTemplates.instantiate(template)
        assertNotEquals(template.id, a.id)
        assertNotEquals(a.id, b.id)
        val stepIds = (a.steps + b.steps).map { it.id }
        assertEquals(stepIds.size, stepIds.toSet().size, "Step ids are unique across uses")
        assertEquals(template.steps.map { it.title }, a.steps.map { it.title })
    }

    @Test
    fun addFromTemplatePutsItLast() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val repo = RoutineRepository(FakeFileStorage(), scope, TestSeedRoutines)
        val added = repo.addFromTemplate(templates.last())
        assertEquals(added, repo.routines.value.last())
        assertEquals(2, repo.routines.value.size)
    }
}
