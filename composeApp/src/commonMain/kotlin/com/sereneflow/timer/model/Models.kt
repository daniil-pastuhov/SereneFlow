package com.sereneflow.timer.model

import kotlinx.serialization.Serializable

@Serializable
enum class VisualEffectType(val displayName: String, val description: String) {
    WATER("Serene Water", "Fluid liquid simulation with gentle waves and buoyant rising bubbles"),
    BONSAI_TREE("Living Bonsai", "Procedural branching tree swaying in the wind with blooming sakura petals"),
    COSMIC_ORBIT("Celestial Orbit", "Gravitational planetary rings and drifting stardust particles"),
    BREATHING_LOTUS("Breathing Lotus", "Sacred geometric petals expanding to a meditative 4-4-6-2 rhythm"),
    HOURGLASS("Zen Hourglass", "Streaming cascade of glowing sand particles and accumulating mound")
}

@Serializable
enum class SoundMode(val displayName: String, val description: String) {
    VOICE_AND_CHIME("Voice & Singing Bowl", "Spoken step guidance with resonant Tibetan singing bowl chimes"),
    VOICE_ONLY("Voice Guidance", "Clear, gentle voice announcing step transitions and milestones"),
    TIBETAN_BOWL("Tibetan Bowl Only", "Harmonic singing bowl bell on step start and completion"),
    NATURE_SOUND("Zen Chime", "Soft wooden wind chime tones on transitions"),
    MUTE("Silent (Visual Only)", "Completely silent with gentle visual pulses")
}

@Serializable
enum class AppThemeMode(val displayName: String) {
    ZEN_SAGE("Zen Sage"),
    OCEAN_MIST("Ocean Mist"),
    SUNSET_CLAY("Sunset Clay"),
    MIDNIGHT_STARLIGHT("Midnight Starlight")
}

@Serializable
data class RoutineStep(
    val id: String,
    val title: String,
    val durationSeconds: Int,
    val description: String = ""
) {
    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return if (minutes > 0 && seconds > 0) {
                "${minutes}m ${seconds}s"
            } else if (minutes > 0) {
                "${minutes}m"
            } else {
                "${seconds}s"
            }
        }
}

@Serializable
data class Routine(
    val id: String,
    val title: String,
    val description: String = "",
    val visualEffect: VisualEffectType = VisualEffectType.WATER,
    val soundMode: SoundMode = SoundMode.VOICE_AND_CHIME,
    val steps: List<RoutineStep> = emptyList(),
    val accentColorHex: Long = 0xFF5B8A72L
) {
    val totalDurationSeconds: Int
        get() = steps.sumOf { it.durationSeconds }

    val formattedTotalDuration: String
        get() {
            val totalMins = totalDurationSeconds / 60
            val secs = totalDurationSeconds % 60
            return if (totalMins > 0 && secs > 0) {
                "${totalMins}m ${secs}s"
            } else if (totalMins > 0) {
                "${totalMins} min"
            } else {
                "${secs} sec"
            }
        }

    val stepCountText: String
        get() = when (steps.size) {
            1 -> "1 step"
            else -> "${steps.size} steps"
        }
}

@Serializable
data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.ZEN_SAGE,
    val defaultVisualEffect: VisualEffectType = VisualEffectType.WATER,
    val defaultSoundMode: SoundMode = SoundMode.VOICE_AND_CHIME,
    /** Percent-of-step-left thresholds that trigger a reminder, e.g. 50 = halfway. */
    val reminderThresholds: Set<Int> = setOf(50),
    /** Keep the display awake while the timer screen is showing. */
    val keepScreenOn: Boolean = true,
    /**
     * Follow the routine with the screen off / app in background: ongoing notification (Android),
     * Live Activity, banners and background audio (iOS). Off until the user opts in.
     */
    val backgroundMode: Boolean = false,
    /** Whether the user has answered the background-mode prompt shown after their first save. */
    val backgroundModeAsked: Boolean = false
) {
    companion object {
        val ReminderOptions = listOf(75, 50, 25, 10)
    }
}

data class TimerState(
    val routine: Routine,
    val currentStepIndex: Int = 0,
    val secondsRemainingInStep: Int = 0,
    /** Wall-clock time (epoch ms) the current step ends; set only while running and not paused. */
    val stepEndEpochMs: Long? = null,
    /** Remaining time of the current step while paused or not yet started. */
    val pausedRemainingMs: Long = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false,
    val isMuted: Boolean = false
) {
    val currentStep: RoutineStep?
        get() = routine.steps.getOrNull(currentStepIndex)

    val nextStep: RoutineStep?
        get() = routine.steps.getOrNull(currentStepIndex + 1)

    fun stepRemainingMsAt(nowEpochMs: Long): Long =
        (stepEndEpochMs?.let { it - nowEpochMs } ?: pausedRemainingMs).coerceAtLeast(0)

    /** Exact progress through the current step at [nowEpochMs], for smooth visuals. */
    fun stepProgressAt(nowEpochMs: Long): Float {
        val step = currentStep ?: return 1f
        if (isCompleted || step.durationSeconds <= 0) return 1f
        val durationMs = step.durationSeconds * 1000f
        return (1f - stepRemainingMsAt(nowEpochMs) / durationMs).coerceIn(0f, 1f)
    }

    val stepProgress: Float
        get() {
            val step = currentStep ?: return 1f
            if (step.durationSeconds <= 0) return 1f
            return 1f - (secondsRemainingInStep.toFloat() / step.durationSeconds.toFloat()).coerceIn(0f, 1f)
        }

    val overallProgress: Float
        get() {
            val total = routine.totalDurationSeconds
            if (total <= 0) return 1f
            val elapsedSoFar = routine.steps.take(currentStepIndex).sumOf { it.durationSeconds } +
                    ((currentStep?.durationSeconds ?: 0) - secondsRemainingInStep)
            return (elapsedSoFar.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

    val formattedRemaining: String
        get() {
            val minutes = secondsRemainingInStep / 60
            val seconds = secondsRemainingInStep % 60
            return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }
}
