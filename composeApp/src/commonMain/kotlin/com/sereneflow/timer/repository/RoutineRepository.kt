package com.sereneflow.timer.repository

import com.sereneflow.timer.model.AppPreferences
import com.sereneflow.timer.model.AppThemeMode
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.model.newStepId
import com.sereneflow.timer.transfer.RoutineTransfer
import kotlin.random.Random
import com.sereneflow.timer.storage.FileStorage
import com.sereneflow.timer.storage.StorageJson
import com.sereneflow.timer.storage.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class RoutineRepository(
    private val storage: FileStorage,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _routines = MutableStateFlow(loadRoutines())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    init {
        // Persist every change in order; conflate so rapid edits write only the latest list.
        _routines.drop(1).conflate()
            .onEach { storage.write(FILE_NAME, StorageJson.encodeToString(it)) }
            .launchIn(scope)
    }

    private fun loadRoutines(): List<Routine> =
        storage.load(FILE_NAME, ::createDefaultRoutines) { json ->
            StorageJson.decodeFromString<List<Routine>>(json).map { it.migrated() }
        }

    fun getRoutine(id: String): Routine? {
        return _routines.value.find { it.id == id }
    }

    /**
     * Replaces the routine with [routine]'s id where it is in the list, or adds [routine] at the
     * end when it is new. The list order is the order the user sees (and can rearrange).
     */
    fun saveRoutine(routine: Routine) {
        _routines.update { current ->
            val index = current.indexOfFirst { it.id == routine.id }
            if (index >= 0) current.toMutableList().apply { set(index, routine) } else current + routine
        }
    }

    /** Adds a new routine made from one of [StarterTemplates], at the end of the list. */
    fun addFromTemplate(template: Routine): Routine {
        val routine = StarterTemplates.instantiate(template)
        saveRoutine(routine)
        return routine
    }

    /**
     * Adds the routines in a `.sereneflow` file ([RoutineTransfer.decode]) at the end of the list,
     * in the file's order, each with a fresh id and fresh step ids so they never replace a routine
     * already here (importing the same file twice gives two copies).
     *
     * @throws IllegalArgumentException when [json] isn't SereneFlow routines.
     */
    fun import(json: String): List<Routine> {
        val imported = RoutineTransfer.decode(json).map { routine ->
            routine.copy(
                id = "routine_${Random.nextLong(Long.MAX_VALUE)}",
                steps = routine.steps.map { it.copy(id = newStepId()) }
            )
        }
        _routines.update { it + imported }
        return imported
    }

    fun deleteRoutine(id: String) = deleteRoutines(setOf(id))

    /** Removes every routine whose id is in [ids]; the rest keep their order. */
    fun deleteRoutines(ids: Set<String>) {
        if (ids.isEmpty()) return
        _routines.update { routines -> routines.filter { it.id !in ids } }
    }

    fun duplicateRoutine(id: String): Routine? {
        val original = getRoutine(id) ?: return null
        return duplicateRoutine(original)
    }

    /** Saves a copy of [original] (which may contain unsaved edits) under a new id, at the end. */
    fun duplicateRoutine(original: Routine): Routine {
        val rand = kotlin.random.Random.nextLong().let { if (it < 0) -it else it }
        val duplicate = original.copy(
            id = "routine_$rand",
            title = "${original.title} (Copy)",
            steps = original.steps.mapIndexed { idx, it -> it.copy(id = "step_${rand}_$idx") }
        )
        saveRoutine(duplicate)
        return duplicate
    }

    companion object {
        const val FILE_NAME = "routines.json"

        fun createDefaultRoutines(): List<Routine> {
            return listOf(
                Routine(
                    id = "morning_awakening",
                    title = "Morning Awakening",
                    description = "Awaken your body and mind with mindful hydration, gentle flow, and breathwork",
                    steps = listOf(
                        RoutineStep("s1", "Warm Lemon Water & Presence", 120, "Hydrate and savor the morning warmth"),
                        RoutineStep("s2", "Cat-Cow & Spine Awakening", 180, "Gentle spinal wave stretches"),
                        RoutineStep("s3", "Box Breathing (4-4-4-4)", 240, "Center your nervous system"),
                        RoutineStep("s4", "Morning Intention Journaling", 360, "Set your positive focus for today")
                    )
                )
            )
        }
    }
}

class PreferencesRepository(
    private val storage: FileStorage,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    init {
        _preferences.drop(1).conflate()
            .onEach { storage.write(FILE_NAME, StorageJson.encodeToString(it)) }
            .launchIn(scope)
    }

    private fun loadPreferences(): AppPreferences =
        storage.load(FILE_NAME, ::AppPreferences) { json ->
            StorageJson.decodeFromString<AppPreferences>(json).migrated()
        }

    fun setThemeMode(mode: AppThemeMode) {
        _preferences.update { it.copy(themeMode = mode) }
    }

    fun setDefaultVisualEffect(effect: VisualEffectType) {
        _preferences.update { it.copy(defaultVisualEffect = effect) }
    }

    fun setDefaultSound(sound: CueSound) {
        _preferences.update { it.copy(defaultSound = sound) }
    }

    fun setDefaultVoice(enabled: Boolean) {
        _preferences.update { it.copy(defaultVoice = enabled) }
    }

    fun setVoice(id: String?) {
        _preferences.update { it.copy(voiceId = id) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _preferences.update { it.copy(keepScreenOn = enabled) }
    }

    fun setLastRoutine(id: String) {
        _preferences.update { if (it.lastRoutineId == id) it else it.copy(lastRoutineId = id) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _preferences.update { it.copy(hapticsEnabled = enabled) }
    }

    fun setBackgroundMode(enabled: Boolean) {
        _preferences.update { it.copy(backgroundMode = enabled, backgroundModeAsked = true) }
    }

    fun toggleReminderThreshold(percent: Int) {
        _preferences.update {
            val current = it.reminderThresholds
            it.copy(reminderThresholds = if (percent in current) current - percent else current + percent)
        }
    }

    companion object {
        const val FILE_NAME = "preferences.json"
    }
}
