package com.sereneflow.timer.audio

import androidx.compose.runtime.Composable
import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.resources.Res
import com.sereneflow.timer.resources.preview_step_title
import org.jetbrains.compose.resources.stringResource

/**
 * What a step's start sounds like with [sound] and [voice]: the timer plays it on every step
 * change, and the pickers play it as a preview, so the two never differ. The tone plays at once;
 * the voice once its words are read.
 */
suspend fun AudioController.playStepCue(
    sound: CueSound,
    voice: Boolean,
    step: RoutineStep,
    stepIndex: Int,
    phrases: SpokenPhrases = ResourcePhrases
) {
    playSound(sound)
    if (voice) {
        // Without a tone to mark the change, the step number does.
        val number = if (sound == CueSound.NONE) stepIndex + 1 else null
        speak(phrases.stepStart(step.title, step.durationSeconds, number))
    }
}

/** The step the pickers announce when previewing a sound or voice. */
@Composable
fun previewStep(): RoutineStep =
    RoutineStep(id = "preview", title = stringResource(Res.string.preview_step_title), durationSeconds = 120)
