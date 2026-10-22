package com.sereneflow.timer.audio

import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.model.RoutineStep

/**
 * What a step's start sounds like with [sound] and [voice]: the timer plays it on every step
 * change, and the pickers play it as a preview, so the two never differ.
 */
fun AudioController.playStepCue(sound: CueSound, voice: Boolean, step: RoutineStep, stepIndex: Int) {
    playSound(sound)
    if (voice) {
        // Without a tone to mark the change, the step number does.
        val prefix = if (sound == CueSound.NONE) "Step ${stepIndex + 1}: " else ""
        speak("$prefix${step.title}, ${step.spokenDuration}")
    }
}

/** The step the pickers announce when previewing a sound or voice. */
val PreviewStep = RoutineStep(id = "preview", title = "Morning stretch", durationSeconds = 120)
