package com.sereneflow.timer.transfer

import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.storage.StorageJson
import com.sereneflow.timer.ui.platform.MIN_STEP_SECONDS
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** The contents of a `.sereneflow` file: shared routines (one or several), or a whole backup. */
@Serializable
data class RoutineBundle(
    val format: String = RoutineTransfer.FORMAT,
    val version: Int = RoutineTransfer.VERSION,
    val routines: List<Routine>
)

/**
 * Routines to and from the `.sereneflow` file format (JSON) used to share a routine and to back
 * up and restore all of them.
 */
object RoutineTransfer {
    const val FORMAT = "sereneflow"
    const val VERSION = 1
    const val FILE_EXTENSION = "sereneflow"
    const val MIME_TYPE = "application/json"

    /** Readable, so a shared file can be looked at; same leniency as the app's own files. */
    private val FileJson = Json(from = StorageJson) { prettyPrint = true }

    fun encode(routines: List<Routine>): String = FileJson.encodeToString(RoutineBundle(routines = routines))

    /**
     * The routines in [json]: a [RoutineBundle], or a bare routine or list of routines. Each is
     * [Routine.migrated], with step lengths brought into range. Ids are kept; callers importing
     * them give them fresh ones.
     *
     * @throws IllegalArgumentException when [json] isn't SereneFlow routines.
     */
    fun decode(json: String): List<Routine> {
        val routines = try {
            when (val element = FileJson.parseToJsonElement(json)) {
                is JsonObject ->
                    if ("routines" in element) FileJson.decodeFromJsonElement<RoutineBundle>(element).routines
                    else listOf(FileJson.decodeFromJsonElement<Routine>(element))
                is JsonArray -> FileJson.decodeFromJsonElement<List<Routine>>(element)
                else -> throw IllegalArgumentException("Not a routine file")
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a routine file", e)
        }
        require(routines.isNotEmpty()) { "The file has no routines" }
        return routines.map { routine ->
            routine.migrated().copy(
                steps = routine.steps.map { it.copy(durationSeconds = it.durationSeconds.coerceIn(MIN_STEP_SECONDS, MAX_STEP_SECONDS)) }
            )
        }
    }

    /** A file name for sharing [routine]: its title, kept to safe characters. */
    fun fileNameFor(routine: Routine): String {
        val base = routine.title.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim().take(40)
        return "${base.ifEmpty { "Routine" }}.$FILE_EXTENSION"
    }

    /** A file name for sharing [routines]: the routine's own for one, a general one for several. */
    fun fileNameFor(routines: List<Routine>): String =
        routines.singleOrNull()?.let(::fileNameFor) ?: SHARED_FILE_NAME

    const val SHARED_FILE_NAME = "SereneFlow Routines.$FILE_EXTENSION"

    const val BACKUP_FILE_NAME = "SereneFlow Backup.$FILE_EXTENSION"

    private const val MAX_STEP_SECONDS = 24 * 60 * 60
}
