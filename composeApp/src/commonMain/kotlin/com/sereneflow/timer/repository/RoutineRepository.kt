package com.sereneflow.timer.repository

import com.sereneflow.timer.model.AppPreferences
import com.sereneflow.timer.model.AppThemeMode
import com.sereneflow.timer.model.Routine
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

class RoutineRepository(
    private val storage: FileStorage,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    /**
     * The routines to start with when there is no file (or one that can't be read): [seedRoutines],
     * whose words are read from resources, so they arrive just after the repository is created.
     */
    defaultRoutines: suspend () -> List<Routine> = ::seedRoutines
) {
    /** No file could be read: [defaultRoutines] are shown, but never written in its place. */
    private var needsSeed = false
    private val _routines = MutableStateFlow(loadRoutines())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    /** The list holding just the default routines, until something changes. */
    @Volatile
    private var seedList: List<Routine>? = null

    init {
        // Persist every change in order; conflate so rapid edits write only the latest list. The
        // default routines alone are not a change, so they never replace a file that exists.
        _routines.drop(1).filter { it !== seedList }.conflate()
            .onEach { storage.write(FILE_NAME, StorageJson.encodeToString(it)) }
            .launchIn(scope)
        if (needsSeed) {
            scope.launch {
                val seed = defaultRoutines()
                seedList = seed
                // Anything added meanwhile (an imported file) follows them, and is saved.
                _routines.update { current -> if (current.isEmpty()) seed else seed + current }
            }
        }
    }

    private fun loadRoutines(): List<Routine> =
        storage.load(FILE_NAME, { needsSeed = true; emptyList() }) { json ->
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
     * already here (importing the same file twice gives two copies), and without a reminder: the
     * sender's daily schedule isn't the user's.
     *
     * @throws IllegalArgumentException when [json] isn't SereneFlow routines.
     */
    fun import(json: String): List<Routine> {
        val imported = RoutineTransfer.decode(json).map { routine ->
            routine.copy(
                id = "routine_${Random.nextLong(Long.MAX_VALUE)}",
                steps = routine.steps.map { it.copy(id = newStepId()) },
                reminder = null
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

    /**
     * Moves the routine with [id] to [toIndex] (kept within the list); the others keep their
     * order. The list order is the order the user sees, saved with the routines.
     */
    fun moveRoutine(id: String, toIndex: Int) {
        _routines.update { routines ->
            val from = routines.indexOfFirst { it.id == id }
            val to = toIndex.coerceIn(0, routines.lastIndex.coerceAtLeast(0))
            if (from < 0 || from == to) routines
            else routines.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    /**
     * Saves a copy of [original] (which may contain unsaved edits) under a new id, at the end,
     * called [title] ("Morning Awakening (Copy)", from resources). The copy has no reminder, so the
     * two don't both notify at the same time.
     */
    fun duplicateRoutine(original: Routine, title: String): Routine {
        val rand = kotlin.random.Random.nextLong().let { if (it < 0) -it else it }
        val duplicate = original.copy(
            id = "routine_$rand",
            title = title,
            steps = original.steps.mapIndexed { idx, it -> it.copy(id = "step_${rand}_$idx") },
            reminder = null
        )
        saveRoutine(duplicate)
        return duplicate
    }

    companion object {
        const val FILE_NAME = "routines.json"
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

    fun setVoiceTipShown() {
        _preferences.update { if (it.voiceTipShown) it else it.copy(voiceTipShown = true) }
    }

    /** Counts a routine run to its end towards the coffee banner. */
    fun recordRoutineCompleted() {
        _preferences.update {
            if (it.hasDonated || it.routinesSinceCoffeePrompt >= AppPreferences.COFFEE_PROMPT_ROUTINES) it
            else it.copy(routinesSinceCoffeePrompt = it.routinesSinceCoffeePrompt + 1)
        }
    }

    /** Closes the coffee banner until another [AppPreferences.COFFEE_PROMPT_ROUTINES] routines are completed. */
    fun dismissCoffeeBanner() {
        _preferences.update { it.copy(routinesSinceCoffeePrompt = 0) }
    }

    fun recordDonation() {
        _preferences.update { if (it.hasDonated) it else it.copy(hasDonated = true) }
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
