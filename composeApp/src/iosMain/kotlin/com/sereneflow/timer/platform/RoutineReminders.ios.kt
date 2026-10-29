package com.sereneflow.timer.platform

import com.sereneflow.timer.model.ReminderSchedule
import com.sereneflow.timer.resources.Res
import com.sereneflow.timer.resources.reminder_notification_body
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Identifier prefix of the reminder notifications, followed by the routine id (and, for a routine
 * reminding on some days only, `.<ISO day>`). The Swift app delegate (ReminderNotifications.swift)
 * shows them in the foreground too, and starts the routine on a tap.
 */
const val REMINDER_ID_PREFIX = "sereneflow.reminder."

/** userInfo key holding the routine id. */
const val REMINDER_ROUTINE_ID = "routineId"

/**
 * The repeating notifications for [reminder]: one a day when it is every day, else one per day
 * of the week (iOS allows 64 pending notifications, so every-day reminders take only one each).
 * Keyed by identifier; null as the day means any day.
 */
private fun requestDays(reminder: RoutineReminder): Map<String, Int?> {
    val base = REMINDER_ID_PREFIX + reminder.routineId
    return if (reminder.schedule.isEveryDay) mapOf(base to null)
    else reminder.schedule.days.associateBy { "$base.$it" }
}

actual suspend fun scheduleRoutineReminders(reminders: List<RoutineReminder>) {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val body = getString(Res.string.reminder_notification_body)
    val requests = reminders.associateWith(::requestDays)
    val identifiers = requests.values.flatMap { it.keys }.toSet()

    center.getPendingNotificationRequestsWithCompletionHandler { pending ->
        val stale = pending.orEmpty()
            .mapNotNull { (it as? UNNotificationRequest)?.identifier }
            .filter { it.startsWith(REMINDER_ID_PREFIX) && it !in identifiers }
        if (stale.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(stale)
    }

    // A request with an identifier already pending replaces it.
    requests.forEach { (reminder, days) ->
        val content = UNMutableNotificationContent().apply {
            setTitle(reminder.routineTitle)
            setBody(body)
            setSound(UNNotificationSound.defaultSound())
            setUserInfo(mapOf<Any?, Any?>(REMINDER_ROUTINE_ID to reminder.routineId))
        }
        days.forEach { (identifier, isoDay) ->
            val components = NSDateComponents().apply {
                hour = reminder.schedule.hour.toLong()
                minute = reminder.schedule.minute.toLong()
                // The Gregorian calendar counts from Sunday = 1.
                if (isoDay != null) weekday = (isoDay % 7 + 1).toLong()
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(identifier, content, trigger),
                withCompletionHandler = null
            )
        }
    }
}

// NSCalendar counts from Sunday = 1.
actual fun firstDayOfWeek(): Int = ((NSCalendar.currentCalendar.firstWeekday.toInt() + 5) % 7) + 1

// Local notifications fire at their minute.
actual fun remindersAreExact(): Boolean = true

actual fun openExactReminderSettings() {}

actual fun formatTimeOfDay(time: ReminderSchedule): String {
    val date = NSCalendar.currentCalendar.dateBySettingHour(
        time.hour.toLong(), minute = time.minute.toLong(), second = 0, ofDate = NSDate(), options = 0u
    ) ?: return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }
    return formatter.stringFromDate(date)
}
