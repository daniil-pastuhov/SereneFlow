package com.sereneflow.timer.repository

import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.resources.*
import org.jetbrains.compose.resources.getString

/** The routine a first launch starts with, in the user's language. */
suspend fun seedRoutines(): List<Routine> = listOf(
    Routine(
        id = "morning_awakening",
        title = getString(Res.string.seed_title),
        description = getString(Res.string.seed_description),
        steps = listOf(
            RoutineStep("s1", getString(Res.string.seed_step_1), 120, getString(Res.string.seed_step_1_description)),
            RoutineStep("s2", getString(Res.string.seed_step_2), 180, getString(Res.string.seed_step_2_description)),
            RoutineStep("s3", getString(Res.string.seed_step_3), 240, getString(Res.string.seed_step_3_description)),
            RoutineStep("s4", getString(Res.string.seed_step_4), 360, getString(Res.string.seed_step_4_description))
        )
    )
)
