package com.sereneflow.timer.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Instant

// The names people see for these choices are string resources (text/ModelText.kt).

@Serializable
enum class VisualEffectType(val emoji: String) {
    WATER("🌊"),
    BONSAI_TREE("🌳"),
    COSMIC_ORBIT("✨"),
    BREATHING_LOTUS("🌸"),
    HOURGLASS("⏳"),

    /** Not an animation: one of the others, picked anew each time the routine starts ([resolved]). */
    RANDOM("🎲");

    /** This effect as one the timer can draw: [RANDOM] becomes one of the [animations]. */
    fun resolved(random: Random = Random.Default): VisualEffectType =
        if (this == RANDOM) animations.random(random) else this

    companion object {
        /** The effects that are an animation of their own, everything but [RANDOM]. */
        val animations: List<VisualEffectType> = entries - RANDOM
    }
}

/** The tone played when a step starts; spoken guidance is chosen separately ([VoiceGuidance]). */
@Serializable
enum class CueSound {
    TIBETAN_BOWL,
    ZEN_CHIME,
    CRYSTAL_BOWL,
    TEMPLE_GONG,
    KALIMBA,
    WOOD_BLOCK,
    KOSHI_CHIMES,
    NONE
}

/** Whether a routine speaks its steps: as Settings says, or always / never. */
@Serializable
enum class VoiceGuidance {
    DEFAULT,
    ON,
    OFF
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

/**
 * What a routine actually looks and sounds like once its Default choices are filled in, and a
 * Random effect picked: [visualEffect] is never [VisualEffectType.RANDOM].
 */
data class RoutineCues(
    val visualEffect: VisualEffectType,
    val sound: CueSound,
    val voice: Boolean
) {
    val isSilent: Boolean
        get() = sound == CueSound.NONE && !voice
}

@Serializable
enum class AppThemeMode {
    ZEN_SAGE,
    OCEAN_MIST,
    SUNSET_CLAY,
    MIDNIGHT_STARLIGHT
}

@Serializable
data class RoutineStep(
    val id: String,
    val title: String,
    val durationSeconds: Int,
    val description: String = ""
)

/**
 * The colours a routine can be tinted with. Mid-tones, so they read on every theme, light or dark.
 * Stored as ARGB in [Routine.accentColorHex].
 */
enum class RoutineAccent(val argb: Long) {
    SAGE(0xFF6FA287L),
    TEAL(0xFF3FA7A0L),
    OCEAN(0xFF4F8FBFL),
    LAVENDER(0xFF8E7CC3L),
    ROSE(0xFFD27D8EL),
    CLAY(0xFFC98458L),
    AMBER(0xFFD4A84BL),
    SLATE(0xFF7A8794L);

    companion object {
        fun of(argb: Long?): RoutineAccent? = entries.firstOrNull { it.argb == argb }
    }
}

/** The accents versions up to 1.0.x stored on every routine without ever showing them. */
private val UnusedLegacyAccents = setOf(0xFF5B8A72L, 0xFF436B54L)

/** A fresh, unique id for a step the editor creates. */
fun newStepId(): String = "step_${Random.nextLong(Long.MAX_VALUE)}"

/**
 * These steps with a copy of the one at [index] inserted right after it, under [newId]. Unchanged
 * when [index] is out of range.
 */
fun List<RoutineStep>.withDuplicatedStep(index: Int, newId: String = newStepId()): List<RoutineStep> {
    val source = getOrNull(index) ?: return this
    return toMutableList().apply { add(index + 1, source.copy(id = newId)) }
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
    /** ARGB colour from [RoutineAccent] tinting the routine's card and timer; null = the theme's. */
    val accentColorHex: Long? = null,
    /** How many times the steps run, one after another (see [expandedForRounds]). */
    val rounds: Int = 1,
    /** A rest between rounds, in seconds; 0 = none. */
    val restBetweenRoundsSeconds: Int = 0,
    /** When the routine sends its daily notification; null = no reminder. */
    val reminder: ReminderSchedule? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("soundMode") val legacySoundMode: LegacySoundMode? = null
) {
    /**
     * This routine as this version stores it: a sound setting from an older version turned into
     * [sound] and [voice], and the accent colour versions up to 1.0.x stored without showing it
     * (always one of two fixed values) cleared, so those routines follow the theme.
     */
    fun migrated(): Routine {
        var routine = this
        if (accentColorHex in UnusedLegacyAccents) routine = routine.copy(accentColorHex = null)
        val legacy = legacySoundMode ?: return routine
        return routine.copy(
            sound = legacy.sound,
            voice = if (legacy.voice) VoiceGuidance.ON else VoiceGuidance.OFF,
            legacySoundMode = null
        )
    }

    /** The routine's cues for one run; a Random effect is picked here, so each run can differ. */
    fun resolve(preferences: AppPreferences, random: Random = Random.Default): RoutineCues = RoutineCues(
        visualEffect = (visualEffect ?: preferences.defaultVisualEffect).resolved(random),
        sound = sound ?: preferences.defaultSound,
        voice = when (voice) {
            VoiceGuidance.DEFAULT -> preferences.defaultVoice
            VoiceGuidance.ON -> true
            VoiceGuidance.OFF -> false
        }
    )

    /** [rounds] kept in range. */
    val roundCount: Int
        get() = rounds.coerceIn(1, MAX_ROUNDS)

    val hasRounds: Boolean
        get() = roundCount > 1

    private val restSeconds: Int
        get() = if (hasRounds) restBetweenRoundsSeconds.coerceAtLeast(0) else 0

    /**
     * This routine as the flat list of steps the timer runs: the steps [roundCount] times, with a
     * [restTitle] step of [restBetweenRoundsSeconds] between rounds (not after the last). Step ids
     * get a round suffix so they stay unique. The timer engine knows nothing of rounds.
     */
    fun expandedForRounds(restTitle: String): Routine {
        if (!hasRounds) return this
        val flat = buildList {
            for (round in 1..roundCount) {
                steps.forEach { add(it.copy(id = "${it.id}_r$round")) }
                if (round < roundCount && restSeconds > 0) add(RoutineStep("rest_r$round", restTitle, restSeconds))
            }
        }
        return copy(steps = flat, rounds = 1, restBetweenRoundsSeconds = 0)
    }

    /** Where step [expandedIndex] of [expandedForRounds] falls in this routine's rounds. */
    fun roundPositionAt(expandedIndex: Int): RoundPosition {
        if (!hasRounds || steps.isEmpty()) return RoundPosition(1, 1, expandedIndex)
        val period = steps.size + if (restSeconds > 0) 1 else 0
        val position = expandedIndex % period
        return RoundPosition(
            round = (expandedIndex / period + 1).coerceAtMost(roundCount),
            rounds = roundCount,
            stepInRound = position.takeIf { it < steps.size }
        )
    }

    val totalDurationSeconds: Int
        get() = steps.sumOf { it.durationSeconds } * roundCount + restSeconds * (roundCount - 1)

    companion object {
        const val MAX_ROUNDS = 99
    }
}

/**
 * When a routine reminds the user: a time of day on the device's clock, on some days of the week
 * ([days] as ISO numbers, 1 = Monday … 7 = Sunday).
 */
@Serializable
data class ReminderSchedule(
    val hour: Int,
    val minute: Int,
    val days: Set<Int> = ALL_DAYS
) {
    /**
     * This schedule kept to a real time of day and real days (a hand-edited file could hold
     * anything). Its [days] may come out empty: such a schedule never fires.
     */
    fun normalized(): ReminderSchedule =
        ReminderSchedule(hour.coerceIn(0, 23), minute.coerceIn(0, 59), days.filter { it in ALL_DAYS }.toSet())

    val isEveryDay: Boolean
        get() = days.containsAll(ALL_DAYS)

    /**
     * The next moment, strictly after [afterEpochMs], that the clock in [zone] shows this time on
     * one of [days]; null when there are no days.
     */
    fun nextAfter(afterEpochMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long? {
        val schedule = normalized()
        if (schedule.days.isEmpty()) return null
        val today = Instant.fromEpochMilliseconds(afterEpochMs).toLocalDateTime(zone).date
        // Within the next 8 days: today's may have passed, and the same weekday comes back a week on.
        // A time a daylight-saving jump skips resolves to just after it.
        return generateSequence(today) { it.plus(1, DateTimeUnit.DAY) }
            .take(8)
            .filter { it.dayOfWeek.isoDayNumber in schedule.days }
            .map { it.atTime(schedule.hour, schedule.minute).toInstant(zone).toEpochMilliseconds() }
            .firstOrNull { it > afterEpochMs }
    }

    companion object {
        val ALL_DAYS: Set<Int> = (1..7).toSet()
        val WEEKDAYS: Set<Int> = (1..5).toSet()
        val WEEKEND: Set<Int> = setOf(6, 7)

        /** What a newly switched-on reminder starts at: every day at 8:00. */
        val Default = ReminderSchedule(8, 0)
    }
}

/**
 * A step's place in a routine with rounds: [round] of [rounds], and its index among the routine's
 * own steps, or null for the rest between rounds.
 */
data class RoundPosition(val round: Int, val rounds: Int, val stepInRound: Int?) {
    val isRest: Boolean get() = stepInRound == null
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
    /** Whether the user has answered the background-mode prompt (first save or first start). */
    val backgroundModeAsked: Boolean = false,
    /** The text-to-speech voice for spoken cues (AudioController.englishVoices); null = default. */
    val voiceId: String? = null,
    /** Vibrate on step changes, the 3-2-1 countdown and completion; independent of mute. */
    val hapticsEnabled: Boolean = true,
    /** The routine started most recently, which quick-start shortcuts and the widget offer first. */
    val lastRoutineId: String? = null,
    /** Whether the timer has shown its one-time tip about voice commands (Siri, Google Assistant). */
    val voiceTipShown: Boolean = false,
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
    val isMuted: Boolean = false,
    /** Time added to the current step while it runs ("+30s"); back to 0 on the next step. */
    val stepExtensionMs: Long = 0
) {
    val currentStep: RoutineStep?
        get() = routine.steps.getOrNull(currentStepIndex)

    val nextStep: RoutineStep?
        get() = routine.steps.getOrNull(currentStepIndex + 1)

    /** The current step's length, including any time added to it. */
    val currentStepDurationMs: Long
        get() = (currentStep?.durationSeconds ?: 0) * 1000L + stepExtensionMs

    fun stepRemainingMsAt(nowEpochMs: Long): Long =
        (stepEndEpochMs?.let { it - nowEpochMs } ?: pausedRemainingMs).coerceAtLeast(0)

    /** Exact progress through the current step at [nowEpochMs], for smooth visuals. */
    fun stepProgressAt(nowEpochMs: Long): Float {
        if (currentStep == null || isCompleted) return 1f
        val durationMs = currentStepDurationMs.toFloat()
        if (durationMs <= 0f) return 1f
        return (1f - stepRemainingMsAt(nowEpochMs) / durationMs).coerceIn(0f, 1f)
    }

    val stepProgress: Float
        get() {
            if (currentStep == null || currentStepDurationMs <= 0) return 1f
            return 1f - (secondsRemainingInStep * 1000f / currentStepDurationMs).coerceIn(0f, 1f)
        }

    val overallProgress: Float
        get() {
            val total = routine.totalDurationSeconds * 1000L + stepExtensionMs
            if (total <= 0) return 1f
            val elapsedSoFar = routine.steps.take(currentStepIndex).sumOf { it.durationSeconds } * 1000L +
                    (currentStepDurationMs - secondsRemainingInStep * 1000L)
            return (elapsedSoFar.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

    val formattedRemaining: String
        get() {
            val minutes = secondsRemainingInStep / 60
            val seconds = secondsRemainingInStep % 60
            return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }
}
