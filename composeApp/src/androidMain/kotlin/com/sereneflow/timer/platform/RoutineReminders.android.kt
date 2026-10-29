package com.sereneflow.timer.platform

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sereneflow.timer.ExternalAction
import com.sereneflow.timer.composeApp.R
import com.sereneflow.timer.model.ReminderSchedule
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.repository.Repositories
import java.util.Calendar

actual suspend fun scheduleRoutineReminders(reminders: List<RoutineReminder>) {
    val context = AndroidAppContext.appContext ?: return
    RoutineReminderAlarms.schedule(context, reminders)
}

actual fun remindersAreExact(): Boolean {
    val context = AndroidAppContext.appContext ?: return true
    return RoutineReminderAlarms.canBeExact(context.getSystemService(AlarmManager::class.java))
}

actual fun openExactReminderSettings() {
    val context = AndroidAppContext.appContext ?: return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        // No such settings page on this device.
    }
}

// Calendar counts from Sunday = 1.
actual fun firstDayOfWeek(): Int = (Calendar.getInstance().firstDayOfWeek + 5) % 7 + 1

actual fun formatTimeOfDay(time: ReminderSchedule): String {
    val context = AndroidAppContext.appContext
        ?: return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, time.hour)
        set(Calendar.MINUTE, time.minute)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

/**
 * One alarm per routine with a reminder, each set for its next time only: when it fires,
 * [RoutineReminderReceiver] posts the notification and sets the next one. Exact where the user
 * allows it ([canBeExact]), otherwise as close as the system lets an inexact alarm come.
 */
internal object RoutineReminderAlarms {
    const val CHANNEL_ID = "routine_reminders"
    const val ACTION_REMIND = "com.sereneflow.timer.action.REMIND"
    const val EXTRA_ROUTINE_ID = "routineId"

    /** The routine ids with an alarm set, so a reminder that was removed can be cancelled. */
    private const val PREFS = "routine_reminders"
    private const val KEY_SCHEDULED = "scheduled"

    /** The activity in androidApp that reads `sereneflow://start` links (by name: it's another module). */
    private const val ACTIVITY_CLASS = "com.sereneflow.timer.SereneFlowActivity"

    fun schedule(context: Context, reminders: List<RoutineReminder>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = reminders.map { it.routineId }.toSet()
        prefs.getStringSet(KEY_SCHEDULED, null).orEmpty().filter { it !in ids }.forEach { cancel(context, it) }
        reminders.forEach { setAlarm(context, it.routineId, it.schedule) }
        prefs.edit().putStringSet(KEY_SCHEDULED, ids).apply()
    }

    fun canBeExact(alarms: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /**
     * Sets [routineId]'s alarm for the first time [schedule] comes after [afterEpochMs], replacing
     * any set before; cancels it when the schedule has no days.
     */
    @SuppressLint("MissingPermission") // Exact only once canBeExact says the user allows it.
    fun setAlarm(context: Context, routineId: String, schedule: ReminderSchedule, afterEpochMs: Long = System.currentTimeMillis()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = schedule.nextAfter(afterEpochMs) ?: return cancel(context, routineId)
        val intent = alarmIntent(context, routineId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (canBeExact(alarms)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
                return
            }
        } catch (_: SecurityException) {
            // The permission was withdrawn just now: fall back to an inexact alarm.
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }

    private fun cancel(context: Context, routineId: String) {
        val intent = alarmIntent(context, routineId, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(intent)
        intent.cancel()
    }

    private fun alarmIntent(context: Context, routineId: String, flags: Int): PendingIntent? {
        val intent = Intent(context, RoutineReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            // The data keeps each routine's PendingIntent apart (extras don't).
            .setData(Uri.fromParts("sereneflow-reminder", routineId, null))
            .putExtra(EXTRA_ROUTINE_ID, routineId)
        return PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    /** "Morning Awakening · Time for your routine"; tapping it starts the routine. */
    @SuppressLint("MissingPermission") // Posting is skipped when notifications are disabled.
    fun notify(context: Context, routine: Routine) {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.reminder_channel_name))
                .setDescription(context.getString(R.string.reminder_channel_description))
                .build()
        )
        val start = Intent(Intent.ACTION_VIEW, Uri.parse(ExternalAction.startUrl(routine.id)))
            .setClassName(context.packageName, ACTIVITY_CLASS)
            // Into the app's task, on its existing activity (onNewIntent) where there is one.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(routine.title)
            .setContentText(context.getString(R.string.reminder_notification_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(context, 0, start, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            .apply { routine.accentColorHex?.let { setColor(it.toInt()) } }
            .build()
        notifications.notify(notificationTag(routine.id), NOTIFICATION_ID, notification)
    }

    private fun notificationTag(routineId: String) = "reminder:$routineId"

    /** One notification per routine, told apart by its tag. */
    private const val NOTIFICATION_ID = 4212
}

/**
 * A routine's reminder alarm: posts its notification and sets the next one (on its next day). Also sets every alarm
 * again after a reboot or an app update (which clear them), after a clock or time zone change,
 * and once exact alarms are allowed. Runs without the UI, possibly in a fresh process.
 */
class RoutineReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AndroidAppContext.appContext == null) AndroidAppContext.appContext = context.applicationContext
        val routines = Repositories.routines.routines.value
        if (intent.action == RoutineReminderAlarms.ACTION_REMIND) {
            val id = intent.getStringExtra(RoutineReminderAlarms.EXTRA_ROUTINE_ID) ?: return
            val routine = routines.firstOrNull { it.id == id }
            // Deleted, or its reminder switched off, since the alarm was set.
            val schedule = routine?.reminder?.normalized() ?: return
            RoutineReminderAlarms.notify(context, routine)
            // A minute on, so an alarm delivered a moment early isn't set again for today.
            RoutineReminderAlarms.setAlarm(context, id, schedule, afterEpochMs = System.currentTimeMillis() + 60_000)
        } else {
            RoutineReminderAlarms.schedule(context, routineReminders(routines))
        }
    }
}
