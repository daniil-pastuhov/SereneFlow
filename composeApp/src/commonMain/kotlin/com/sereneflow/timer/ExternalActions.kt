package com.sereneflow.timer

import com.sereneflow.timer.model.Routine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The routine [idOrTitle] names: by id, else by title (exact, then partial), ignoring case. */
fun findRoutine(routines: List<Routine>, idOrTitle: String): Routine? {
    val query = idOrTitle.trim()
    return routines.firstOrNull { it.id == query }
        ?: routines.firstOrNull { it.title.equals(query, ignoreCase = true) }
        ?: routines.firstOrNull { query.isNotEmpty() && it.title.contains(query, ignoreCase = true) }
}

/** Something asked of the app from outside its own screens; [App] carries it out. */
sealed interface ExternalAction {
    /** Import the routines in a `.sereneflow` file's [contents] (opened from another app or picked). */
    data class ImportRoutines(val contents: String) : ExternalAction

    /**
     * Start a routine (a launcher shortcut, the widget, Siri, a `sereneflow://start` link): its id
     * or, failing that, its title (voice assistants know routines by name), or [LAST_ROUTINE] for
     * the one started most recently. Ignored while a routine is running.
     */
    data class StartRoutine(val routineId: String) : ExternalAction

    companion object {
        const val LAST_ROUTINE = "last"
        const val URL_SCHEME = "sereneflow"

        /** `sereneflow://start?routine=<id>`, which [parseUrl] reads back. */
        fun startUrl(routineId: String): String = "$URL_SCHEME://start?routine=${encode(routineId)}"

        /** The action a `sereneflow://` link asks for, or null for anything else. */
        fun parseUrl(url: String): ExternalAction? {
            val prefix = "$URL_SCHEME://start"
            if (!url.startsWith(prefix, ignoreCase = true)) return null
            val query = url.substringAfter('?', "")
            val id = query.split('&')
                .map { it.substringBefore('=') to it.substringAfter('=', "") }
                // "name" is what Google Assistant fills in (androidApp res/xml/shortcuts.xml).
                .firstOrNull { it.first == "routine" || it.first == "name" }?.second
                ?.let(::decode)
                ?.takeIf { it.isNotBlank() }
                ?: LAST_ROUTINE
            return StartRoutine(id)
        }

        // Ids are "routine_<digits>" or seed names, but a link may come from anywhere.
        private fun encode(value: String): String = buildString {
            value.encodeToByteArray().forEach { byte ->
                val c = byte.toInt().toChar()
                if (byte >= 0 && (c.isLetterOrDigit() || c == '_' || c == '-' || c == '.')) append(c)
                else append('%').append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
            }
        }

        private fun decode(value: String): String? = try {
            val bytes = ArrayList<Byte>()
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '%' && i + 2 <= value.lastIndex) {
                    bytes.add(value.substring(i + 1, i + 3).toInt(16).toByte())
                    i += 3
                } else {
                    c.toString().encodeToByteArray().forEach { bytes.add(it) }
                    i++
                }
            }
            bytes.toByteArray().decodeToString()
        } catch (_: NumberFormatException) {
            null
        }
    }
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
