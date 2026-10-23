package com.sereneflow.timer.repository

import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineAccent
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.model.newStepId
import kotlin.random.Random

/** Ready-made routines offered by "New from template"; each use gets fresh ids ([instantiate]). */
object StarterTemplates {
    val all: List<Routine> = listOf(
        template(
            "Pomodoro",
            "Four focused work blocks with short breaks, then a long rest",
            RoutineAccent.CLAY,
            VisualEffectType.HOURGLASS,
            "Focus" to 25 * 60, "Short break" to 5 * 60,
            "Focus" to 25 * 60, "Short break" to 5 * 60,
            "Focus" to 25 * 60, "Short break" to 5 * 60,
            "Focus" to 25 * 60, "Long break" to 15 * 60
        ),
        template(
            "Box Breathing",
            "Settle, breathe in a steady 4-4-4-4 square, then let the breath go",
            RoutineAccent.OCEAN,
            VisualEffectType.BREATHING_LOTUS,
            "Settle in" to 60,
            "Box breathing (4-4-4-4)" to 4 * 60,
            "Natural breath" to 60
        ),
        template(
            "Morning Stretch",
            "A gentle head-to-toe stretch to wake the body",
            RoutineAccent.AMBER,
            VisualEffectType.BONSAI_TREE,
            "Neck rolls" to 60,
            "Shoulder circles" to 60,
            "Cat-cow" to 90,
            "Side stretch" to 60,
            "Forward fold" to 60,
            "Child's pose" to 90
        ),
        template(
            "HIIT",
            "Four high-intensity moves with short rests, warmed up and cooled down",
            RoutineAccent.ROSE,
            VisualEffectType.COSMIC_ORBIT,
            "Warm-up" to 3 * 60,
            "Jumping jacks" to 40, "Rest" to 20,
            "Squats" to 40, "Rest" to 20,
            "Push-ups" to 40, "Rest" to 20,
            "Mountain climbers" to 40, "Rest" to 20,
            "Cool-down" to 3 * 60
        ),
        template(
            "Wind-down",
            "Slow the evening down before sleep",
            RoutineAccent.LAVENDER,
            VisualEffectType.WATER,
            "Dim the lights" to 2 * 60,
            "Gentle stretch" to 5 * 60,
            "Body scan" to 5 * 60,
            "Gratitude" to 3 * 60,
            "4-7-8 breathing" to 2 * 60
        )
    )

    /** [template] as a new routine of the user's: its own routine id and step ids. */
    fun instantiate(template: Routine): Routine = template.copy(
        id = "routine_${Random.nextLong(Long.MAX_VALUE)}",
        steps = template.steps.map { it.copy(id = newStepId()) }
    )

    private fun template(
        title: String,
        description: String,
        accent: RoutineAccent,
        effect: VisualEffectType,
        vararg steps: Pair<String, Int>
    ) = Routine(
        id = "template_${title.lowercase().replace(' ', '_')}",
        title = title,
        description = description,
        visualEffect = effect,
        accentColorHex = accent.argb,
        steps = steps.mapIndexed { index, (name, seconds) -> RoutineStep("t$index", name, seconds) }
    )
}
