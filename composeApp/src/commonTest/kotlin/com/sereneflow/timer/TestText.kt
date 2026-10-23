package com.sereneflow.timer

import com.sereneflow.timer.audio.SpokenPhrases
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineStep

/*
 * Host tests can't read Compose resources (they need an Android Resources instance), so these
 * stand in for the English strings. StringResourcesTest checks they say what strings.xml says.
 */

/** The English phrases of composeResources/values/strings.xml. */
object EnglishPhrases : SpokenPhrases {
    override suspend fun stepStart(title: String, durationSeconds: Int, number: Int?): String =
        (if (number != null) "Step $number: " else "") + "$title, ${englishSpokenDuration(durationSeconds)}"

    override suspend fun reminder(stepTitle: String, percentLeft: Int): String = "$stepTitle, $percentLeft percent left"

    override suspend fun routineComplete(): String = "Routine complete. Well done."
}

fun englishSpokenDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    fun unit(count: Int, name: String) = if (count == 1) "1 $name" else "$count ${name}s"
    return when {
        minutes > 0 && seconds > 0 -> "${unit(minutes, "minute")} ${unit(seconds, "second")}"
        minutes > 0 -> unit(minutes, "minute")
        else -> unit(seconds, "second")
    }
}

/** The seed routine, as RoutineRepository gets it from resources (seedRoutines) in English. */
val TestSeedRoutines: suspend () -> List<Routine> = {
    listOf(
        Routine(
            id = "morning_awakening",
            title = "Morning Awakening",
            steps = listOf(
                RoutineStep("s1", "Warm Lemon Water & Presence", 120),
                RoutineStep("s2", "Cat-Cow & Spine Awakening", 180),
                RoutineStep("s3", "Box Breathing (4-4-4-4)", 240),
                RoutineStep("s4", "Morning Intention Journaling", 360)
            )
        )
    )
}
