package com.sereneflow.timer.audio

import com.sereneflow.timer.resources.Res
import com.sereneflow.timer.resources.speech_reminder
import com.sereneflow.timer.resources.speech_routine_complete
import com.sereneflow.timer.resources.speech_step
import com.sereneflow.timer.resources.speech_step_numbered
import com.sereneflow.timer.text.spokenDurationText
import org.jetbrains.compose.resources.getString

/** What the timer says. Suspending, so the words can be read from resources. */
interface SpokenPhrases {
    /**
     * A step starting: "Breathe, 2 minutes", or "Step 2: Breathe, 2 minutes" with its [number]
     * when no tone marks the change.
     */
    suspend fun stepStart(title: String, durationSeconds: Int, number: Int?): String

    /** "Breathe, 50 percent left". */
    suspend fun reminder(stepTitle: String, percentLeft: Int): String

    suspend fun routineComplete(): String
}

/** The app's phrases, from its string resources in the user's language. */
object ResourcePhrases : SpokenPhrases {
    override suspend fun stepStart(title: String, durationSeconds: Int, number: Int?): String {
        val duration = spokenDurationText(durationSeconds)
        return if (number != null) getString(Res.string.speech_step_numbered, number, title, duration)
        else getString(Res.string.speech_step, title, duration)
    }

    override suspend fun reminder(stepTitle: String, percentLeft: Int): String =
        getString(Res.string.speech_reminder, stepTitle, percentLeft)

    override suspend fun routineComplete(): String = getString(Res.string.speech_routine_complete)
}
