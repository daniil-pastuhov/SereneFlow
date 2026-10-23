package com.sereneflow.timer.text

import androidx.compose.runtime.Composable
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.resources.Res
import com.sereneflow.timer.resources.duration_minutes
import com.sereneflow.timer.resources.duration_minutes_seconds
import com.sereneflow.timer.resources.duration_seconds
import com.sereneflow.timer.resources.duration_short_min
import com.sereneflow.timer.resources.duration_short_min_sec
import com.sereneflow.timer.resources.duration_short_sec
import com.sereneflow.timer.resources.duration_total_min
import com.sereneflow.timer.resources.duration_total_sec
import com.sereneflow.timer.resources.routine_summary
import com.sereneflow.timer.resources.step_count
import com.sereneflow.timer.resources.step_count_rounds
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Durations and counts as the app writes and says them. Each comes as a composable, for screens,
 * and as a suspend function, for text made outside the composition (speech, shortcuts).
 */

/** Picks [both], [minutesOnly] or [secondsOnly] for [totalSeconds], with the minutes and seconds. */
private inline fun <T> byParts(
    totalSeconds: Int,
    both: (minutes: Int, seconds: Int) -> T,
    minutesOnly: (minutes: Int) -> T,
    secondsOnly: (seconds: Int) -> T
): T {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes > 0 && seconds > 0 -> both(minutes, seconds)
        minutes > 0 -> minutesOnly(minutes)
        else -> secondsOnly(seconds)
    }
}

/** A step's length, short: "1m 30s", "2m", "45s". */
@Composable
fun shortDuration(totalSeconds: Int): String = byParts(
    totalSeconds,
    both = { m, s -> stringResource(Res.string.duration_short_min_sec, m, s) },
    minutesOnly = { m -> stringResource(Res.string.duration_short_min, m) },
    secondsOnly = { s -> stringResource(Res.string.duration_short_sec, s) }
)

/** A routine's whole length: "15m 30s", "15 min", "45 sec". */
@Composable
fun totalDuration(totalSeconds: Int): String = byParts(
    totalSeconds,
    both = { m, s -> stringResource(Res.string.duration_short_min_sec, m, s) },
    minutesOnly = { m -> stringResource(Res.string.duration_total_min, m) },
    secondsOnly = { s -> stringResource(Res.string.duration_total_sec, s) }
)

suspend fun totalDurationText(totalSeconds: Int): String = byParts(
    totalSeconds,
    both = { m, s -> getString(Res.string.duration_short_min_sec, m, s) },
    minutesOnly = { m -> getString(Res.string.duration_total_min, m) },
    secondsOnly = { s -> getString(Res.string.duration_total_sec, s) }
)

/**
 * [totalSeconds] in words, e.g. "1 minute 30 seconds", for speech and screen readers, which would
 * read "2m" as meters and "01:30" as a time of day.
 */
@Composable
fun spokenDuration(totalSeconds: Int): String = byParts(
    totalSeconds,
    both = { m, s ->
        stringResource(
            Res.string.duration_minutes_seconds,
            pluralStringResource(Res.plurals.duration_minutes, m, m),
            pluralStringResource(Res.plurals.duration_seconds, s, s)
        )
    },
    minutesOnly = { m -> pluralStringResource(Res.plurals.duration_minutes, m, m) },
    secondsOnly = { s -> pluralStringResource(Res.plurals.duration_seconds, s, s) }
)

suspend fun spokenDurationText(totalSeconds: Int): String = byParts(
    totalSeconds,
    both = { m, s ->
        getString(
            Res.string.duration_minutes_seconds,
            getPluralString(Res.plurals.duration_minutes, m, m),
            getPluralString(Res.plurals.duration_seconds, s, s)
        )
    },
    minutesOnly = { m -> getPluralString(Res.plurals.duration_minutes, m, m) },
    secondsOnly = { s -> getPluralString(Res.plurals.duration_seconds, s, s) }
)

/** "3 steps", or "3 steps × 4" with rounds. */
@Composable
fun stepCountText(routine: Routine): String {
    val steps = pluralStringResource(Res.plurals.step_count, routine.steps.size, routine.steps.size)
    return if (routine.hasRounds) stringResource(Res.string.step_count_rounds, steps, routine.roundCount) else steps
}

suspend fun stepCountString(routine: Routine): String {
    val steps = getPluralString(Res.plurals.step_count, routine.steps.size, routine.steps.size)
    return if (routine.hasRounds) getString(Res.string.step_count_rounds, steps, routine.roundCount) else steps
}

/** "4 steps · 15 min": a routine in a line, for shortcuts and the widget. */
suspend fun routineSummaryText(routine: Routine): String =
    getString(Res.string.routine_summary, stepCountString(routine), totalDurationText(routine.totalDurationSeconds))
