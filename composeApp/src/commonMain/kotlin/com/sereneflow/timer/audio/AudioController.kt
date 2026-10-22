package com.sereneflow.timer.audio

interface AudioController {
    fun playTibetanBowl(fundamentalHz: Double = 432.0, durationMs: Long = 3000)
    fun playZenChime(frequencyHz: Double = 720.0, durationMs: Long = 1400)
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
}

expect fun createAudioController(): AudioController
