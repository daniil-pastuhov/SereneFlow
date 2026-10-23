package com.sereneflow.timer.audio

import com.sereneflow.timer.model.CueSound

/** An English text-to-speech voice installed on the device. */
data class VoiceOption(val id: String, val name: String, val detail: String)

interface AudioController {
    fun playTibetanBowl(fundamentalHz: Double = 432.0, durationMs: Long = 3000)
    fun playZenChime(frequencyHz: Double = 720.0, durationMs: Long = 1400)
    /** Plays a step-start [sound] ([ToneSynth.render]); nothing for [CueSound.NONE]. */
    fun playSound(sound: CueSound)
    fun playStepComplete()
    fun playRoutineComplete()
    fun speak(text: String)
    fun stop()

    /**
     * Called while a routine is ticking (true) or paused/stopped (false). iOS keeps its audio
     * output running during a session so the app stays alive in the background and cues keep
     * playing; other platforms keep the process alive by other means.
     */
    fun setSessionActive(active: Boolean) {}

    /**
     * English voices installed on the device, best quality first; the prompts are English, so
     * other languages are left out. Empty when text-to-speech isn't available.
     */
    suspend fun englishVoices(): List<VoiceOption> = emptyList()

    /** The voice [speak] uses from now on: an id from [englishVoices], or null for the default. */
    fun setVoice(id: String?) {}

    /** A short vibration, felt with the sound off or the phone in a pocket; nothing where unsupported. */
    fun haptic(kind: HapticKind) {}
}

/** The moments the timer marks with a vibration. */
enum class HapticKind {
    /** A step begins. */
    STEP_START,

    /** Each second of the 3-2-1 countdown at the end of a step. */
    COUNTDOWN_TICK,

    /** The routine is finished. */
    ROUTINE_COMPLETE
}

expect fun createAudioController(): AudioController
