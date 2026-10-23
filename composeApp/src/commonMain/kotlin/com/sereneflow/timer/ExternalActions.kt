package com.sereneflow.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Something asked of the app from outside its own screens; [App] carries it out. */
sealed interface ExternalAction {
    /** Import the routines in a `.sereneflow` file's [contents] (opened from another app or picked). */
    data class ImportRoutines(val contents: String) : ExternalAction
}

/**
 * Hands requests from the platform (a file opened with the app, a file picker's result) to the
 * running UI. Process-wide, so a request posted before the UI exists waits until [App] takes it.
 */
object ExternalActions {
    private val _pending = MutableStateFlow<ExternalAction?>(null)
    val pending: StateFlow<ExternalAction?> = _pending.asStateFlow()

    fun post(action: ExternalAction) {
        _pending.value = action
    }

    /** Marks [action] as handled, unless a newer one has arrived since. */
    fun consume(action: ExternalAction) {
        _pending.compareAndSet(action, null)
    }
}
