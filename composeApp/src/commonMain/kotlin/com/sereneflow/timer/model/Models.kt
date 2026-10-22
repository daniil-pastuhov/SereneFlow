package com.sereneflow.timer.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VisualEffectType(val displayName: String, val description: String, val emoji: String) {
    WATER("Serene Water", "Fluid liquid simulation with gentle waves and buoyant rising bubbles", "🌊"),
    BONSAI_TREE("Living Bonsai", "Procedural branching tree swaying in the wind with blooming sakura petals", "🌳"),
    COSMIC_ORBIT("Celestial Orbit", "Gravitational planetary rings and drifting stardust particles", "✨"),
    BREATHING_LOTUS("Breathing Lotus", "Sacred geometric petals expanding to a meditative 4-4-6-2 rhythm", "🌸"),
    HOURGLASS("Zen Hourglass", "Streaming cascade of glowing sand particles and accumulating mound", "⏳")
}

/** The tone played when a step starts; spoken guidance is chosen separately ([VoiceGuidance]). */
@Serializable
enum class CueSound(val displayName: String, val description: String) {
    TIBETAN_BOWL("Tibetan Bowl", "Resonant singing bowl with a slow shimmer"),
    ZEN_CHIME("Zen Chime", "Soft, bright chime tone"),
    CRYSTAL_BOWL("Crystal Bowl", "Pure, long-ringing quartz bowl"),
    TEMPLE_GONG("Temple Gong", "Deep, low gong that fades slowly"),
    KALIMBA("Kalimba", "Gentle plucked thumb-piano note"),
    WOOD_BLOCK("Wood Block", "Short, warm wooden knock"),
    KOSHI_CHIMES("Koshi Chimes", "A small cascade of wind-chime notes"),
    NONE("No Sound", "No tone; only the voice, if it is on")
}

/** Whether a routine speaks its steps: as Settings says, or always / never. */
@Serializable
enum class VoiceGuidance(val displayName: String) {
    DEFAULT("Default"),
    ON("On"),
    OFF("Off")
}

/**
 * The sound setting of app versions up to 1.x, which mixed the tone and the voice. Only read from
 * saved files, and turned into a [CueSound] and voice by [migrated].
 */
@Serializable
enum class LegacySoundMode(val sound: CueSound, val voice: Boolean) {
    VOICE_AND_CHIME(CueSound.TIBETAN_BOWL, true),
    VOICE_ONLY(CueSound.NONE, true),
    TIBETAN_BOWL(CueSound.TIBETAN_BOWL, false),
    NATURE_SOUND(CueSound.ZEN_CHIME, false),
    MUTE(CueSound.NONE, false)
}

/** What a routine actually looks and sounds like once its Default choices are filled in. */
data class RoutineCues(
    val visualEffect: VisualEffectType,
    val sound: CueSound,
    val voice: Boolean
) {
    val isSilent: Boolean
        get() = sound == CueSound.NONE && !voice
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

    /** The duration as the voice says it, e.g. "1 minute 30 seconds"; "2m" can be read as meters. */
    val spokenDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            fun unit(count: Int, name: String) = if (count == 1) "1 $name" else "$count ${name}s"
            return when {
                minutes > 0 && seconds > 0 -> "${unit(minutes, "minute")} ${unit(seconds, "second")}"
                minutes > 0 -> unit(minutes, "minute")
                else -> unit(seconds, "second")
            }
        }
}

@Serializable
data class Routine(
    val id: String,
    val title: String,
    val description: String = "",
    /** null = the default from Settings, read when the routine starts. */
    val visualEffect: VisualEffectType? = null,
    /** null = the default from Settings, read when the routine starts. */
    val sound: CueSound? = null,
    val voice: VoiceGuidance = VoiceGuidance.DEFAULT,
    val steps: List<RoutineStep> = emptyList(),
    val accentColorHex: Long = 0xFF5B8A72L,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("soundMode") val legacySoundMode: LegacySoundMode? = null
) {
    /** This routine with a sound setting from an older version turned into [sound] and [voice]. */
    fun migrated(): Routine {
        val legacy = legacySoundMode ?: return this
        return copy(
            sound = legacy.sound,
            voice = if (legacy.voice) VoiceGuidance.ON else VoiceGuidance.OFF,
            legacySoundMode = null
        )
    }

    fun resolve(preferences: AppPreferences): RoutineCues = RoutineCues(
        visualEffect = visualEffect ?: preferences.defaultVisualEffect,
        sound = sound ?: preferences.defaultSound,
        voice = when (voice) {
            VoiceGuidance.DEFAULT -> preferences.defaultVoice
            VoiceGuidance.ON -> true
            VoiceGuidance.OFF -> false
        }
    )

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
    val defaultSound: CueSound = CueSound.TIBETAN_BOWL,
    /** Whether routines whose voice is Default speak their steps. */
    val defaultVoice: Boolean = true,
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
    val backgroundModeAsked: Boolean = false,
    /** The text-to-speech voice for spoken cues (AudioController.englishVoices); null = default. */
    val voiceId: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("defaultSoundMode") val legacyDefaultSoundMode: LegacySoundMode? = null
) {
    /** These preferences with a default sound from an older version turned into the new two. */
    fun migrated(): AppPreferences {
        val legacy = legacyDefaultSoundMode ?: return this
        return copy(defaultSound = legacy.sound, defaultVoice = legacy.voice, legacyDefaultSoundMode = null)
    }

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
