package com.sereneflow.timer.repository

import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineAccent
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.model.newStepId
import com.sereneflow.timer.resources.*
import org.jetbrains.compose.resources.StringResource
import kotlin.random.Random

/**
 * A ready-made routine offered by "New from template". Its words are string resources, looked up
 * in the user's language when it is shown or added ([toRoutine]).
 */
class StarterTemplate(
    val id: String,
    val title: StringResource,
    val description: StringResource,
    val accent: RoutineAccent,
    val effect: VisualEffectType,
    val steps: List<Pair<StringResource, Int>>
) {
    /** This template as a routine, its words looked up by [text] (stringResource, getString). */
    inline fun toRoutine(text: (StringResource) -> String): Routine = Routine(
        id = id,
        title = text(title),
        description = text(description),
        visualEffect = effect,
        accentColorHex = accent.argb,
        steps = steps.mapIndexed { index, (name, seconds) -> RoutineStep("t$index", text(name), seconds) }
    )
}

/** The starter templates; each use gets fresh ids ([instantiate]). */
object StarterTemplates {
    val all: List<StarterTemplate> = listOf(
        StarterTemplate(
            "template_pomodoro",
            Res.string.template_pomodoro,
            Res.string.template_pomodoro_description,
            RoutineAccent.CLAY,
            VisualEffectType.HOURGLASS,
            listOf(
                Res.string.template_pomodoro_focus to 25 * 60, Res.string.template_pomodoro_short_break to 5 * 60,
                Res.string.template_pomodoro_focus to 25 * 60, Res.string.template_pomodoro_short_break to 5 * 60,
                Res.string.template_pomodoro_focus to 25 * 60, Res.string.template_pomodoro_short_break to 5 * 60,
                Res.string.template_pomodoro_focus to 25 * 60, Res.string.template_pomodoro_long_break to 15 * 60
            )
        ),
        StarterTemplate(
            "template_box_breathing",
            Res.string.template_box_breathing,
            Res.string.template_box_breathing_description,
            RoutineAccent.OCEAN,
            VisualEffectType.BREATHING_LOTUS,
            listOf(
                Res.string.template_box_breathing_settle to 60,
                Res.string.template_box_breathing_box to 4 * 60,
                Res.string.template_box_breathing_natural to 60
            )
        ),
        StarterTemplate(
            "template_morning_stretch",
            Res.string.template_morning_stretch,
            Res.string.template_morning_stretch_description,
            RoutineAccent.AMBER,
            VisualEffectType.BONSAI_TREE,
            listOf(
                Res.string.template_morning_stretch_neck to 60,
                Res.string.template_morning_stretch_shoulders to 60,
                Res.string.template_morning_stretch_cat_cow to 90,
                Res.string.template_morning_stretch_side to 60,
                Res.string.template_morning_stretch_fold to 60,
                Res.string.template_morning_stretch_child to 90
            )
        ),
        StarterTemplate(
            "template_hiit",
            Res.string.template_hiit,
            Res.string.template_hiit_description,
            RoutineAccent.ROSE,
            VisualEffectType.COSMIC_ORBIT,
            listOf(
                Res.string.template_hiit_warm_up to 3 * 60,
                Res.string.template_hiit_jumping_jacks to 40, Res.string.template_hiit_rest to 20,
                Res.string.template_hiit_squats to 40, Res.string.template_hiit_rest to 20,
                Res.string.template_hiit_push_ups to 40, Res.string.template_hiit_rest to 20,
                Res.string.template_hiit_mountain_climbers to 40, Res.string.template_hiit_rest to 20,
                Res.string.template_hiit_cool_down to 3 * 60
            )
        ),
        StarterTemplate(
            "template_wind-down",
            Res.string.template_wind_down,
            Res.string.template_wind_down_description,
            RoutineAccent.LAVENDER,
            VisualEffectType.WATER,
            listOf(
                Res.string.template_wind_down_lights to 2 * 60,
                Res.string.template_wind_down_stretch to 5 * 60,
                Res.string.template_wind_down_body_scan to 5 * 60,
                Res.string.template_wind_down_gratitude to 3 * 60,
                Res.string.template_wind_down_478 to 2 * 60
            )
        )
    )

    /** [template] as a new routine of the user's: its own routine id and step ids. */
    fun instantiate(template: Routine): Routine = template.copy(
        id = "routine_${Random.nextLong(Long.MAX_VALUE)}",
        steps = template.steps.map { it.copy(id = newStepId()) }
    )
}
