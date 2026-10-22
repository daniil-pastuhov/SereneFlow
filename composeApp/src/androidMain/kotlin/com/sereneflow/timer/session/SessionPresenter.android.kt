package com.sereneflow.timer.session

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sereneflow.timer.platform.AndroidAppContext

/**
 * Mirrors the session into a foreground-service notification. The service is started once when
 * the routine begins (the app is in the foreground then, as Android 12+ requires); later changes
 * just re-post the notification.
 */
private class AndroidSessionPresenter(private val context: Context) : SessionPresenter {
    private val notifications = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission") // Posting is skipped when notifications are disabled.
    override fun show(snapshot: SessionSnapshot) {
        TimerNotifications.ensureChannel(context)

        if (snapshot.isCompleted) {
            stopService()
            if (notifications.areNotificationsEnabled()) {
                // Separate id: the system removes the service's own notification when it stops.
                notifications.notify(TimerNotifications.COMPLETE_ID, TimerNotifications.buildComplete(context, snapshot))
            }
            return
        }

        val notification = TimerNotifications.buildOngoing(context, snapshot)
        TimerService.pendingNotification = notification
        TimerService.wantsWakeLock = !snapshot.isPaused

        val service = TimerService.instance
        if (service == null) {
            ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
        } else {
            service.updateWakeLock()
            if (notifications.areNotificationsEnabled()) {
                notifications.notify(TimerNotifications.ONGOING_ID, notification)
            }
        }
    }

    override fun end() {
        stopService()
        notifications.cancel(TimerNotifications.ONGOING_ID)
        notifications.cancel(TimerNotifications.COMPLETE_ID)
    }

    private fun stopService() {
        TimerService.pendingNotification = null
        TimerService.wantsWakeLock = false
        context.stopService(Intent(context, TimerService::class.java))
    }
}

actual fun createSessionPresenter(): SessionPresenter {
    val context = checkNotNull(AndroidAppContext.appContext) {
        "AndroidAppContext.appContext must be set before starting a session"
    }
    return AndroidSessionPresenter(context)
}

@Composable
actual fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        {
            val needsRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsRequest) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
