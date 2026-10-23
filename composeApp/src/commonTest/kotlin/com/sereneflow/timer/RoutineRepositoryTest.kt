package com.sereneflow.timer

import com.sereneflow.timer.model.AppThemeMode
import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.model.VoiceGuidance
import com.sereneflow.timer.repository.PreferencesRepository
import com.sereneflow.timer.repository.RoutineRepository
import com.sereneflow.timer.repository.StarterTemplates
import com.sereneflow.timer.storage.FileStorage
import com.sereneflow.timer.transfer.RoutineTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineRepositoryTest {

    private fun TestScope.persistenceScope() =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.routineRepo(storage: FileStorage) = RoutineRepository(storage, persistenceScope(), TestSeedRoutines)

    private fun TestScope.prefsRepo(storage: FileStorage) = PreferencesRepository(storage, persistenceScope())

    @Test
    fun corruptFileIsBackedUpAndNotOverwritten() = runTest {
        val corrupt = """[{"id":"a","title":"""
        val storage = FakeFileStorage().apply { files[RoutineRepository.FILE_NAME] = corrupt }
        val repo = routineRepo(storage)

        assertEquals("Morning Awakening", repo.routines.value.single().title, "Falls back to the seed in memory")
        assertEquals(corrupt, storage.files[RoutineRepository.FILE_NAME], "The seed isn't written over the file")
        assertEquals(corrupt, storage.files["${RoutineRepository.FILE_NAME}.bad"])

        // A real change is saved, and the backup still holds the original.
        repo.saveRoutine(repo.routines.value.single().copy(title = "Edited"))
        assertEquals("Edited", routineRepo(storage).routines.value.single().title)
        assertEquals(corrupt, storage.files["${RoutineRepository.FILE_NAME}.bad"])
    }

    @Test
    fun unreadableFileIsNotOverwritten() = runTest {
        val storage = object : FileStorage {
            val writes = mutableListOf<String>()
            override fun read(name: String): String = throw IllegalStateException("locked")
            override fun write(name: String, content: String) {
                writes += name
            }
        }
        val routines = routineRepo(storage)
        val prefs = prefsRepo(storage)
        assertEquals(1, routines.routines.value.size)
        assertEquals(AppThemeMode.ZEN_SAGE, prefs.preferences.value.themeMode)
        assertFalse(storage.writes.any(), "Nothing is written until the user changes something")
    }

    @Test
    fun corruptPreferencesAreBackedUp() = runTest {
        val storage = FakeFileStorage().apply { files[PreferencesRepository.FILE_NAME] = "{nope" }
        assertEquals(AppThemeMode.ZEN_SAGE, prefsRepo(storage).preferences.value.themeMode)
        assertEquals("{nope", storage.files[PreferencesRepository.FILE_NAME])
        assertEquals("{nope", storage.files["${PreferencesRepository.FILE_NAME}.bad"])
    }

    @Test
    fun unknownEnumValuesAreCoerced() = runTest {
        // Values a newer version might write: the rest of the file must still load.
        val storage = FakeFileStorage().apply {
            files[RoutineRepository.FILE_NAME] = """[
                {"id":"a","title":"A","visualEffect":"LAVA_LAMP","sound":"DIDGERIDOO","voice":"WHISPER",
                 "steps":[{"id":"s","title":"S","durationSeconds":30}]}
            ]"""
            files[PreferencesRepository.FILE_NAME] =
                """{"themeMode":"NEON","defaultVisualEffect":"LAVA_LAMP","defaultSound":"KALIMBA","keepScreenOn":false}"""
        }

        val routine = routineRepo(storage).routines.value.single()
        assertEquals("A", routine.title)
        assertNull(routine.visualEffect)
        assertNull(routine.sound)
        assertEquals(VoiceGuidance.DEFAULT, routine.voice)
        assertEquals(1, routine.steps.size)
        assertFalse("${RoutineRepository.FILE_NAME}.bad" in storage.files)

        val prefs = prefsRepo(storage).preferences.value
        assertEquals(AppThemeMode.ZEN_SAGE, prefs.themeMode)
        assertEquals(VisualEffectType.WATER, prefs.defaultVisualEffect)
        assertEquals(CueSound.KALIMBA, prefs.defaultSound)
        assertEquals(false, prefs.keepScreenOn)
    }

    private fun routine(id: String) = Routine(id = id, title = id, steps = listOf(RoutineStep("s_$id", "Step", 30)))

    @Test
    fun newRoutinesAreAddedAtTheEnd() = runTest {
        val storage = FakeFileStorage()
        val repo = routineRepo(storage)
        val seed = repo.routines.value.single()

        repo.saveRoutine(routine("a"))
        repo.saveRoutine(routine("b"))
        val copy = repo.duplicateRoutine(repo.getRoutine("a")!!, "a (Copy)")
        val template = repo.addFromTemplate(StarterTemplates.all.first().toRoutine { it.key })
        val imported = repo.import(RoutineTransfer.encode(listOf(routine("c")))).single()

        val expected = listOf(seed.id, "a", "b", copy.id, template.id, imported.id)
        assertEquals(expected, repo.routines.value.map { it.id })
        assertEquals(expected, routineRepo(storage).routines.value.map { it.id }, "The order is saved")
    }

    @Test
    fun editingKeepsTheRoutinesPlace() = runTest {
        val repo = routineRepo(FakeFileStorage())
        repo.saveRoutine(routine("a"))
        repo.saveRoutine(routine("b"))
        repo.saveRoutine(routine("a").copy(title = "Edited"))
        assertEquals(listOf("Morning Awakening", "Edited", "b"), repo.routines.value.map { it.title })
    }

    @Test
    fun deleteManyKeepsTheRestInOrder() = runTest {
        val storage = FakeFileStorage()
        val repo = routineRepo(storage)
        listOf("a", "b", "c", "d").forEach { repo.saveRoutine(routine(it)) }

        repo.deleteRoutines(setOf("a", "c", "not-there"))
        assertEquals(listOf("Morning Awakening", "b", "d"), repo.routines.value.map { it.title })
        assertEquals(listOf("Morning Awakening", "b", "d"), routineRepo(storage).routines.value.map { it.title })

        repo.deleteRoutines(emptySet())
        assertEquals(3, repo.routines.value.size)
    }

    @Test
    fun sharingSeveralRoutinesRoundTripsAsOneFile() = runTest {
        val from = routineRepo(FakeFileStorage())
        listOf("a", "b", "c").forEach { from.saveRoutine(routine(it)) }
        val shared = from.routines.value.filter { it.id in setOf("c", "a") }
        val file = RoutineTransfer.encode(shared)
        assertEquals("SereneFlow Routines.sereneflow", RoutineTransfer.fileNameFor(shared, "Routine", "SereneFlow Routines"))
        assertEquals("a.sereneflow", RoutineTransfer.fileNameFor(shared.take(1), "Routine", "SereneFlow Routines"))

        val to = routineRepo(FakeFileStorage())
        val imported = to.import(file)
        assertEquals(listOf("a", "c"), imported.map { it.title }, "In list order")
        assertEquals(listOf("Morning Awakening", "a", "c"), to.routines.value.map { it.title })
        assertEquals(shared.map { it.steps.map { s -> s.title to s.durationSeconds } }, imported.map { it.steps.map { s -> s.title to s.durationSeconds } })
    }

    @Test
    fun movingARoutineReordersAndPersists() = runTest {
        val storage = FakeFileStorage()
        val repo = routineRepo(storage)
        repo.deleteRoutine(repo.routines.value.single().id)
        listOf("a", "b", "c", "d").forEach { repo.saveRoutine(routine(it)) }

        repo.moveRoutine("a", 2)
        assertEquals(listOf("b", "c", "a", "d"), repo.routines.value.map { it.id })
        repo.moveRoutine("d", 0)
        assertEquals(listOf("d", "b", "c", "a"), repo.routines.value.map { it.id })
        // Move up / down past the ends stays in the list; unknown ids change nothing.
        repo.moveRoutine("d", -1)
        repo.moveRoutine("a", 9)
        repo.moveRoutine("zzz", 1)
        assertEquals(listOf("d", "b", "c", "a"), repo.routines.value.map { it.id })

        assertEquals(listOf("d", "b", "c", "a"), routineRepo(storage).routines.value.map { it.id }, "The order is saved")
    }
}
