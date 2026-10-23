package com.sereneflow.timer

import com.sereneflow.timer.model.AppPreferences
import com.sereneflow.timer.model.AppThemeMode
import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineCues
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

    private fun TestScope.routineRepo(storage: FileStorage) = RoutineRepository(storage, persistenceScope(), TestSeedRoutines)

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

        val copy = repo.duplicateRoutine(original.copy(title = "Edited"), "Edited (Copy)")
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
        prefs.setDefaultSound(CueSound.TEMPLE_GONG)
        prefs.setDefaultVoice(false)
        prefs.toggleReminderThreshold(25)
        prefs.toggleReminderThreshold(50) // default on, toggled off
        prefs.setKeepScreenOn(false)
        prefs.setBackgroundMode(true)

        val reloaded = prefsRepo(storage).preferences.value
        assertEquals(AppThemeMode.OCEAN_MIST, reloaded.themeMode)
        assertEquals(VisualEffectType.HOURGLASS, reloaded.defaultVisualEffect)
        assertEquals(CueSound.TEMPLE_GONG, reloaded.defaultSound)
        assertEquals(false, reloaded.defaultVoice)
        assertEquals(setOf(25), reloaded.reminderThresholds)
        assertEquals(false, reloaded.keepScreenOn)
        assertEquals(true, reloaded.backgroundMode)
        assertEquals(true, reloaded.backgroundModeAsked, "Answering the prompt marks it as asked")
    }

    @Test
    fun soundSettingsFromOlderVersionsAreSplitIntoSoundAndVoice() = runTest {
        val storage = FakeFileStorage().apply {
            files[RoutineRepository.FILE_NAME] = """[
                {"id":"a","title":"A","visualEffect":"BONSAI_TREE","soundMode":"VOICE_AND_CHIME"},
                {"id":"b","title":"B","visualEffect":"WATER","soundMode":"VOICE_ONLY"},
                {"id":"c","title":"C","visualEffect":"WATER","soundMode":"TIBETAN_BOWL"},
                {"id":"d","title":"D","visualEffect":"WATER","soundMode":"NATURE_SOUND"},
                {"id":"e","title":"E","visualEffect":"WATER","soundMode":"MUTE"}
            ]"""
            files[PreferencesRepository.FILE_NAME] = """{"defaultSoundMode":"NATURE_SOUND"}"""
        }

        val routines = routineRepo(storage).routines.value.associateBy { it.id }
        assertEquals(5, routines.size, "No routine is lost to the old format")
        assertEquals(CueSound.TIBETAN_BOWL to VoiceGuidance.ON, routines.getValue("a").let { it.sound to it.voice })
        assertEquals(CueSound.NONE to VoiceGuidance.ON, routines.getValue("b").let { it.sound to it.voice })
        assertEquals(CueSound.TIBETAN_BOWL to VoiceGuidance.OFF, routines.getValue("c").let { it.sound to it.voice })
        assertEquals(CueSound.ZEN_CHIME to VoiceGuidance.OFF, routines.getValue("d").let { it.sound to it.voice })
        assertEquals(CueSound.NONE to VoiceGuidance.OFF, routines.getValue("e").let { it.sound to it.voice })
        assertEquals(VisualEffectType.BONSAI_TREE, routines.getValue("a").visualEffect)

        val prefs = prefsRepo(storage).preferences.value
        assertEquals(CueSound.ZEN_CHIME, prefs.defaultSound)
        assertEquals(false, prefs.defaultVoice)
    }

    @Test
    fun legacySoundFieldIsNotWrittenBack() = runTest {
        val storage = FakeFileStorage().apply {
            files[RoutineRepository.FILE_NAME] = """[{"id":"a","title":"A","soundMode":"MUTE"}]"""
        }
        val repo = routineRepo(storage)
        repo.saveRoutine(repo.routines.value.single().copy(title = "Renamed"))
        val written = storage.files.getValue(RoutineRepository.FILE_NAME)
        assertTrue("soundMode" !in written, written)
        assertEquals(CueSound.NONE, routineRepo(storage).routines.value.single().sound)
    }

    @Test
    fun defaultChoicesResolveFromPreferences() {
        val prefs = AppPreferences(
            defaultVisualEffect = VisualEffectType.HOURGLASS,
            defaultSound = CueSound.KALIMBA,
            defaultVoice = false
        )
        val defaults = Routine(id = "r", title = "R")
        assertEquals(RoutineCues(VisualEffectType.HOURGLASS, CueSound.KALIMBA, voice = false), defaults.resolve(prefs))

        val own = defaults.copy(
            visualEffect = VisualEffectType.WATER,
            sound = CueSound.NONE,
            voice = VoiceGuidance.ON
        )
        assertEquals(RoutineCues(VisualEffectType.WATER, CueSound.NONE, voice = true), own.resolve(prefs))
    }
}
