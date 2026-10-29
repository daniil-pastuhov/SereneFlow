package com.sereneflow.timer.text

import androidx.compose.runtime.Composable
import com.sereneflow.timer.model.AppThemeMode
import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.model.ReminderSchedule
import com.sereneflow.timer.model.RoutineAccent
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.model.VoiceGuidance
import com.sereneflow.timer.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * What the model's choices are called on screen. The model stays free of Compose and of words;
 * these map each entry to its string resources.
 */

val VisualEffectType.nameRes: StringResource
    get() = when (this) {
        VisualEffectType.WATER -> Res.string.effect_water
        VisualEffectType.BONSAI_TREE -> Res.string.effect_bonsai
        VisualEffectType.COSMIC_ORBIT -> Res.string.effect_orbit
        VisualEffectType.BREATHING_LOTUS -> Res.string.effect_lotus
        VisualEffectType.HOURGLASS -> Res.string.effect_hourglass
        VisualEffectType.RANDOM -> Res.string.effect_random
    }

val VisualEffectType.descriptionRes: StringResource
    get() = when (this) {
        VisualEffectType.WATER -> Res.string.effect_water_description
        VisualEffectType.BONSAI_TREE -> Res.string.effect_bonsai_description
        VisualEffectType.COSMIC_ORBIT -> Res.string.effect_orbit_description
        VisualEffectType.BREATHING_LOTUS -> Res.string.effect_lotus_description
        VisualEffectType.HOURGLASS -> Res.string.effect_hourglass_description
        VisualEffectType.RANDOM -> Res.string.effect_random_description
    }

/** The short name on routine cards: "Water", "Orbit". */
val VisualEffectType.shortNameRes: StringResource
    get() = when (this) {
        VisualEffectType.WATER -> Res.string.effect_water_short
        VisualEffectType.BONSAI_TREE -> Res.string.effect_bonsai_short
        VisualEffectType.COSMIC_ORBIT -> Res.string.effect_orbit_short
        VisualEffectType.BREATHING_LOTUS -> Res.string.effect_lotus_short
        VisualEffectType.HOURGLASS -> Res.string.effect_hourglass_short
        VisualEffectType.RANDOM -> Res.string.effect_random_short
    }

val CueSound.nameRes: StringResource
    get() = when (this) {
        CueSound.TIBETAN_BOWL -> Res.string.sound_tibetan_bowl
        CueSound.ZEN_CHIME -> Res.string.sound_zen_chime
        CueSound.CRYSTAL_BOWL -> Res.string.sound_crystal_bowl
        CueSound.TEMPLE_GONG -> Res.string.sound_temple_gong
        CueSound.KALIMBA -> Res.string.sound_kalimba
        CueSound.WOOD_BLOCK -> Res.string.sound_wood_block
        CueSound.KOSHI_CHIMES -> Res.string.sound_koshi_chimes
        CueSound.NONE -> Res.string.sound_none
    }

val CueSound.descriptionRes: StringResource
    get() = when (this) {
        CueSound.TIBETAN_BOWL -> Res.string.sound_tibetan_bowl_description
        CueSound.ZEN_CHIME -> Res.string.sound_zen_chime_description
        CueSound.CRYSTAL_BOWL -> Res.string.sound_crystal_bowl_description
        CueSound.TEMPLE_GONG -> Res.string.sound_temple_gong_description
        CueSound.KALIMBA -> Res.string.sound_kalimba_description
        CueSound.WOOD_BLOCK -> Res.string.sound_wood_block_description
        CueSound.KOSHI_CHIMES -> Res.string.sound_koshi_chimes_description
        CueSound.NONE -> Res.string.sound_none_description
    }

val VoiceGuidance.nameRes: StringResource
    get() = when (this) {
        VoiceGuidance.DEFAULT -> Res.string.value_default
        VoiceGuidance.ON -> Res.string.value_on
        VoiceGuidance.OFF -> Res.string.value_off
    }

val AppThemeMode.nameRes: StringResource
    get() = when (this) {
        AppThemeMode.ZEN_SAGE -> Res.string.theme_zen_sage
        AppThemeMode.OCEAN_MIST -> Res.string.theme_ocean_mist
        AppThemeMode.SUNSET_CLAY -> Res.string.theme_sunset_clay
        AppThemeMode.MIDNIGHT_STARLIGHT -> Res.string.theme_midnight_starlight
    }

val RoutineAccent.nameRes: StringResource
    get() = when (this) {
        RoutineAccent.SAGE -> Res.string.accent_sage
        RoutineAccent.TEAL -> Res.string.accent_teal
        RoutineAccent.OCEAN -> Res.string.accent_ocean
        RoutineAccent.LAVENDER -> Res.string.accent_lavender
        RoutineAccent.ROSE -> Res.string.accent_rose
        RoutineAccent.CLAY -> Res.string.accent_clay
        RoutineAccent.AMBER -> Res.string.accent_amber
        RoutineAccent.SLATE -> Res.string.accent_slate
    }

@Composable
fun VisualEffectType.displayName(): String = stringResource(nameRes)

@Composable
fun VisualEffectType.description(): String = stringResource(descriptionRes)

@Composable
fun CueSound.displayName(): String = stringResource(nameRes)

@Composable
fun CueSound.description(): String = stringResource(descriptionRes)

@Composable
fun VoiceGuidance.displayName(): String = stringResource(nameRes)

@Composable
fun AppThemeMode.displayName(): String = stringResource(nameRes)

@Composable
fun RoutineAccent.displayName(): String = stringResource(nameRes)

private val WeekdayShortNames = listOf(
    Res.string.weekday_short_1, Res.string.weekday_short_2, Res.string.weekday_short_3, Res.string.weekday_short_4,
    Res.string.weekday_short_5, Res.string.weekday_short_6, Res.string.weekday_short_7
)

private val WeekdayNames = listOf(
    Res.string.weekday_1, Res.string.weekday_2, Res.string.weekday_3, Res.string.weekday_4,
    Res.string.weekday_5, Res.string.weekday_6, Res.string.weekday_7
)

/** "Mon" for ISO day 1 (Monday) … "Sun" for 7. */
@Composable
fun weekdayShortName(isoDay: Int): String = stringResource(WeekdayShortNames[isoDay - 1])

/** "Monday" for ISO day 1 … "Sunday" for 7. */
@Composable
fun weekdayName(isoDay: Int): String = stringResource(WeekdayNames[isoDay - 1])

/** The ISO days of a week that starts on [firstDay]: 7, 1, 2, … 6 for a week starting on Sunday. */
fun weekStartingOn(firstDay: Int): List<Int> = (0..6).map { (firstDay - 1 + it) % 7 + 1 }

/**
 * When [schedule] reminds, with [time] already formatted: "Every day at 7:30 AM", "Weekdays at
 * 7:30 AM", or its days in the order of a week starting on [firstDay]: "Mon, Wed, Fri at 7:30 AM".
 */
@Composable
fun reminderScheduleText(schedule: ReminderSchedule, time: String, firstDay: Int): String {
    val days = schedule.normalized().days
    return when (days) {
        ReminderSchedule.ALL_DAYS -> stringResource(Res.string.editor_reminder_on_detail, time)
        ReminderSchedule.WEEKDAYS -> stringResource(Res.string.editor_reminder_weekdays, time)
        ReminderSchedule.WEEKEND -> stringResource(Res.string.editor_reminder_weekends, time)
        else -> stringResource(
            Res.string.editor_reminder_days,
            weekStartingOn(firstDay).filter { it in days }.map { weekdayShortName(it) }.joinToString(", "),
            time
        )
    }
}
