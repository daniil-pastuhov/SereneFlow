package com.sereneflow.timer.platform

import com.sereneflow.timer.model.Routine

/** One routine offered for a one-tap start outside the app. */
data class QuickStartItem(val routineId: String, val title: String, val subtitle: String)

/** At most this many routines are offered (launchers show about four shortcuts). */
const val MAX_QUICK_START_ITEMS = 4

/** The routines to offer: the last one started first, then the list's order. */
fun quickStartItems(routines: List<Routine>, lastRoutineId: String?): List<QuickStartItem> {
    val last = routines.firstOrNull { it.id == lastRoutineId }
    return (listOfNotNull(last) + routines.filter { it !== last })
        .filter { it.steps.isNotEmpty() }
        .take(MAX_QUICK_START_ITEMS)
        .map { QuickStartItem(it.id, it.title, "${it.stepCountText} · ${it.formattedTotalDuration}") }
}

/**
 * Publishes [items] where the system offers them: Android launcher shortcuts (long-press the
 * icon), iOS Home Screen quick actions, and the iOS widget's data where an App Group is set up.
 * Each opens the app and starts its routine.
 */
expect fun publishQuickStart(items: List<QuickStartItem>)
