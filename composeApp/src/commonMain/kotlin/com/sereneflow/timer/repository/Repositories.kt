package com.sereneflow.timer.repository

import com.sereneflow.timer.storage.createFileStorage

/**
 * The repositories, created once per process. They load their file when created and write every
 * change back, so a second instance (e.g. from an activity recreation re-running App()) would be a
 * second writer racing the first, and would miss changes the first hasn't written yet.
 */
object Repositories {
    private val storage by lazy { createFileStorage() }
    val routines: RoutineRepository by lazy { RoutineRepository(storage) }
    val preferences: PreferencesRepository by lazy { PreferencesRepository(storage) }
}
