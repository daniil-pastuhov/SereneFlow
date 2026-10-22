package com.sereneflow.timer.audio

import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.model.SoundMode

/**
 * What a step's start sounds like in [mode]: the timer plays it on every step change, and
 * Settings plays it as a preview when a sound is picked, so the two never differ.
 */
fun AudioController.playStepCue(mode: SoundMode, step: RoutineStep, stepIndex: Int) {
    when (mode) {
        SoundMode.VOICE_AND_CHIME -> {
            playTibetanBowl()
            speak("${step.title}, ${step.formattedDuration}")
        }
        SoundMode.VOICE_ONLY -> {
            speak("Step ${stepIndex + 1}: ${step.title}, ${step.formattedDuration}")
        }
        SoundMode.TIBETAN_BOWL -> {
            playTibetanBowl()
        }
        SoundMode.NATURE_SOUND -> {
            playZenChime()
        }
        SoundMode.MUTE -> {}
    }
}

/** The step Settings announces when previewing a sound. */
val PreviewStep = RoutineStep(id = "preview", title = "Morning stretch", durationSeconds = 120)
