package com.sereneflow.timer.session

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sereneflow.timer.composeApp.R

internal object TimerNotifications {
    const val CHANNEL_ID = "routine_timer"
    const val ONGOING_ID = 4210
    const val COMPLETE_ID = 4211

    fun ensureChannel(context: Context) {
        // Low importance: the routine plays its own bowl/voice cues, so the notification stays silent.
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notification_channel_name))
            .setDescription(context.getString(R.string.notification_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * Ongoing notification for a running or paused routine. The countdown uses the system
     * chronometer, so it ticks without the app posting updates. On Android 16+ it is requested
     * as a Live Update (status-bar chip, promoted on the lock screen) with one progress segment
     * per step.
     */
    fun buildOngoing(context: Context, snapshot: SessionSnapshot): Notification {
        val builder = baseBuilder(context, snapshot)
            .setContentTitle(snapshot.stepTitle)
            .setContentText(statusLine(context, snapshot))
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setStyle(progressStyle(snapshot))

        val stepEnd = snapshot.stepEndEpochMs
        if (stepEnd != null && !snapshot.isPaused) {
            builder.setWhen(stepEnd)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
                .setShortCriticalText(context.getString(R.string.notification_paused))
        }

        builder.addAction(
            0,
            context.getString(if (snapshot.isPaused) R.string.notification_action_resume else R.string.notification_action_pause),
            actionIntent(context, TimerActionReceiver.ACTION_TOGGLE)
        )
        builder.addAction(0, context.getString(R.string.notification_action_skip), actionIntent(context, TimerActionReceiver.ACTION_SKIP))
        builder.addAction(0, context.getString(R.string.notification_action_end), actionIntent(context, TimerActionReceiver.ACTION_END))
        return builder.build()
    }

    fun buildComplete(context: Context, snapshot: SessionSnapshot): Notification =
        baseBuilder(context, snapshot)
            .setContentTitle(context.getString(R.string.notification_complete_title))
            .setContentText(context.getString(R.string.notification_complete_text, snapshot.routineTitle))
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

    /** Shown only for the instant the service needs to satisfy startForeground after the session ended. */
    fun buildPlaceholder(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(context.getString(R.string.notification_ended))
            .setSilent(true)
            .build()

    private fun baseBuilder(context: Context, snapshot: SessionSnapshot) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setSubText(snapshot.routineTitle)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(context))
            .apply { snapshot.accentArgb?.let { setColor(it.toInt()) } }

    private fun statusLine(context: Context, snapshot: SessionSnapshot): String {
        val stepPosition = context.getString(R.string.notification_step_position, snapshot.stepIndex + 1, snapshot.stepCount)
        val step = if (snapshot.rounds > 1) {
            context.getString(R.string.notification_round_step_position, snapshot.round, snapshot.rounds, stepPosition)
        } else stepPosition
        return when {
            snapshot.isPaused -> context.getString(R.string.notification_status_paused, formatRemaining(snapshot.stepRemainingMs), step)
            snapshot.nextStepTitle != null -> context.getString(R.string.notification_status_next, step, snapshot.nextStepTitle)
            else -> context.getString(R.string.notification_status_last, step)
        }
    }

    private fun progressStyle(snapshot: SessionSnapshot): NotificationCompat.ProgressStyle {
        val elapsedInStepMs = snapshot.stepDurationMs - snapshot.stepRemainingMs
        val elapsedSeconds = ((snapshot.elapsedBeforeStepMs + elapsedInStepMs) / 1000).toInt()
        return NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(
                snapshot.stepDurationsSeconds.map { NotificationCompat.ProgressStyle.Segment(it.coerceAtLeast(1)) }
            )
            .setProgress(elapsedSeconds.coerceAtLeast(0))
    }

    private fun openAppIntent(context: Context): PendingIntent {
        // The launcher's intent, whichever theme's icon alias is enabled, with the launcher's flags:
        // brings the existing task to the front instead of a new one. Pinned to this package so it
        // can never resolve to another app.
        val intent = (
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            )
            .setPackage(context.packageName)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, TimerActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun formatRemaining(ms: Long): String {
        val totalSeconds = ((ms + 999) / 1000).toInt()
        return "${(totalSeconds / 60).toString().padStart(2, '0')}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    }
}
