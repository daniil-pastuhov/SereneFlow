package com.sereneflow.timer.platform

import com.sereneflow.timer.model.ReminderSchedule
import com.sereneflow.timer.model.Routine

/** A routine's reminder, as the system schedules it; [schedule] is normalized and has days. */
data class RoutineReminder(val routineId: String, val routineTitle: String, val schedule: ReminderSchedule)

/** The reminders [routines] ask for, in list order. A schedule left with no days asks for none. */
fun routineReminders(routines: List<Routine>): List<RoutineReminder> =
    routines.mapNotNull { routine ->
        routine.reminder?.normalized()?.takeIf { it.days.isNotEmpty() }
            ?.let { RoutineReminder(routine.id, routine.title, it) }
    }

/**
 * Schedules a notification at each reminder's time on its days, naming its routine; tapping it
 * starts the routine. Replaces whatever was scheduled before, so a routine left out (deleted, or
 * its reminder switched off) stops reminding. Android: an alarm per routine, set again after
 * each one fires and after a reboot. iOS: repeating local notifications.
 */
expect suspend fun scheduleRoutineReminders(reminders: List<RoutineReminder>)

/**
 * Whether a reminder can fire at its exact minute. False on Android 12+ until the user allows
 * Alarms & reminders for the app: the system may then deliver it some minutes late.
 */
expect fun remindersAreExact(): Boolean

/** Opens the system page where the user allows exact alarms ([remindersAreExact]); no-op where there is none. */
expect fun openExactReminderSettings()

/** The day weeks start on where the user lives, as an ISO number (1 = Monday, 7 = Sunday). */
expect fun firstDayOfWeek(): Int

/** [time]'s hour and minute as the device's clock shows it: "7:30 AM" or "07:30", following the 12/24-hour setting. */
expect fun formatTimeOfDay(time: ReminderSchedule): String
