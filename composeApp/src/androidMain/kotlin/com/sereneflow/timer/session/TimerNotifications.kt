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
            .setName("Running routine")
            .setDescription("Shows the current step and time left while a routine runs")
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
            .setContentText(statusLine(snapshot))
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
                .setShortCriticalText("Paused")
        }

        builder.addAction(
            0,
            if (snapshot.isPaused) "Resume" else "Pause",
            actionIntent(context, TimerActionReceiver.ACTION_TOGGLE)
        )
        builder.addAction(0, "Skip", actionIntent(context, TimerActionReceiver.ACTION_SKIP))
        builder.addAction(0, "End", actionIntent(context, TimerActionReceiver.ACTION_END))
        return builder.build()
    }

    fun buildComplete(context: Context, snapshot: SessionSnapshot): Notification =
        baseBuilder(context, snapshot)
            .setContentTitle("Routine complete")
            .setContentText("You finished \"${snapshot.routineTitle}\". Take a moment to notice how you feel.")
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

    /** Shown only for the instant the service needs to satisfy startForeground after the session ended. */
    fun buildPlaceholder(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Routine ended")
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

    private fun statusLine(snapshot: SessionSnapshot): String {
        val step = "Step ${snapshot.stepIndex + 1} of ${snapshot.stepCount}"
        return when {
            snapshot.isPaused -> "Paused · ${formatRemaining(snapshot.stepRemainingMs)} left · $step"
            snapshot.nextStepTitle != null -> "$step · Up next: ${snapshot.nextStepTitle}"
            else -> "$step · Final step"
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
        // Same flags as the launcher: brings the existing task to the front instead of a new one.
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        } ?: Intent()
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
