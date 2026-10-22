package com.sereneflow.timer

import com.sereneflow.timer.model.AppThemeMode
import com.sereneflow.timer.model.SoundMode
import com.sereneflow.timer.model.VisualEffectType
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeFileStorage : FileStorage {
    val files = mutableMapOf<String, String>()
    override fun read(name: String): String? = files[name]
    override fun write(name: String, content: String) {
        files[name] = content
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryPersistenceTest {

    // Unconfined so the persistence collectors write synchronously on each change.
    private fun TestScope.persistenceScope() =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.routineRepo(storage: FileStorage) = RoutineRepository(storage, persistenceScope())

    private fun TestScope.prefsRepo(storage: FileStorage) = PreferencesRepository(storage, persistenceScope())

    @Test
    fun firstLaunchSeedsSingleRoutine() = runTest {
        val repo = routineRepo(FakeFileStorage())
        val routines = repo.routines.value
        assertEquals(1, routines.size)
        assertEquals("Morning Awakening", routines.single().title)
    }

    @Test
    fun editsSurviveReload() = runTest {
        val storage = FakeFileStorage()
        val repo = routineRepo(storage)
        val original = repo.routines.value.single()
        repo.saveRoutine(original.copy(title = "My Morning"))

        val reloaded = routineRepo(storage)
        assertEquals("My Morning", reloaded.routines.value.single().title)
        assertEquals(original.accentColorHex, reloaded.routines.value.single().accentColorHex)
    }

    @Test
    fun deleteAllStaysEmptyAfterReload() = runTest {
        val storage = FakeFileStorage()
        val repo = routineRepo(storage)
        repo.deleteRoutine(repo.routines.value.single().id)

        val reloaded = routineRepo(storage)
        assertTrue(reloaded.routines.value.isEmpty(), "Deleting the seed must not bring it back")
    }

    @Test
    fun duplicateKeepsEditsAndOriginal() = runTest {
        val storage = FakeFileStorage()
        val repo = routineRepo(storage)
        val original = repo.routines.value.single()

        val copy = repo.duplicateRoutine(original.copy(title = "Edited"))
        assertEquals("Edited (Copy)", copy.title)
        assertEquals(original.steps.size, copy.steps.size)

        val reloaded = routineRepo(storage)
        assertEquals(2, reloaded.routines.value.size)
        assertNotNull(reloaded.getRoutine(original.id))
        assertEquals("Morning Awakening", reloaded.getRoutine(original.id)?.title)
    }

    @Test
    fun newPreferenceDefaults() = runTest {
        val prefs = prefsRepo(FakeFileStorage()).preferences.value
        assertEquals(true, prefs.keepScreenOn)
        assertEquals(false, prefs.backgroundMode)
        assertEquals(false, prefs.backgroundModeAsked)
        assertEquals(null, prefs.voiceId)
    }

    @Test
    fun preferencesFromBeforeVoicesDecode() = runTest {
        // A 1.0 preferences.json has no voiceId: it must load with the default voice, not reset.
        val storage = FakeFileStorage().apply {
            files["preferences.json"] = """{"themeMode":"SUNSET_CLAY","keepScreenOn":false}"""
        }
        val prefs = prefsRepo(storage).preferences.value
        assertEquals(AppThemeMode.SUNSET_CLAY, prefs.themeMode)
        assertEquals(false, prefs.keepScreenOn)
        assertEquals(null, prefs.voiceId)
    }

    @Test
    fun voiceSurvivesReloadAndClears() = runTest {
        val storage = FakeFileStorage()
        prefsRepo(storage).setVoice("en-us-x-tpc-local")
        val reloaded = prefsRepo(storage)
        assertEquals("en-us-x-tpc-local", reloaded.preferences.value.voiceId)

        reloaded.setVoice(null)
        assertEquals(null, prefsRepo(storage).preferences.value.voiceId)
    }

    @Test
    fun corruptFileFallsBackToSeed() = runTest {
        val storage = FakeFileStorage().apply { files[RoutineRepository.FILE_NAME] = "{not json" }
        val repo = routineRepo(storage)
        assertEquals(1, repo.routines.value.size)
    }

    @Test
    fun preferencesSurviveReload() = runTest {
        val storage = FakeFileStorage()
        val prefs = prefsRepo(storage)
        prefs.setThemeMode(AppThemeMode.OCEAN_MIST)
        prefs.setDefaultVisualEffect(VisualEffectType.HOURGLASS)
        prefs.setDefaultSoundMode(SoundMode.MUTE)
        prefs.toggleReminderThreshold(25)
        prefs.toggleReminderThreshold(50) // default on, toggled off
        prefs.setKeepScreenOn(false)
        prefs.setBackgroundMode(true)

        val reloaded = prefsRepo(storage).preferences.value
        assertEquals(AppThemeMode.OCEAN_MIST, reloaded.themeMode)
        assertEquals(VisualEffectType.HOURGLASS, reloaded.defaultVisualEffect)
        assertEquals(SoundMode.MUTE, reloaded.defaultSoundMode)
        assertEquals(setOf(25), reloaded.reminderThresholds)
        assertEquals(false, reloaded.keepScreenOn)
        assertEquals(true, reloaded.backgroundMode)
        assertEquals(true, reloaded.backgroundModeAsked, "Answering the prompt marks it as asked")
    }
}
