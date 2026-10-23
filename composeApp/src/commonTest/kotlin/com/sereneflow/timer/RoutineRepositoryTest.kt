package com.sereneflow.timer

import com.sereneflow.timer.model.AppThemeMode
import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.model.VoiceGuidance
import com.sereneflow.timer.repository.PreferencesRepository
import com.sereneflow.timer.repository.RoutineRepository
import com.sereneflow.timer.storage.FileStorage
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

    private fun TestScope.routineRepo(storage: FileStorage) = RoutineRepository(storage, persistenceScope())

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
}
