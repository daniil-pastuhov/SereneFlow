package com.sereneflow.timer.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Handles the Pause/Resume, Skip and End buttons on the routine notification. */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (TimerSession.active.value == null) {
            // Process was restarted and the session is gone; clear the stale notification.
            NotificationManagerCompat.from(context).cancel(TimerNotifications.ONGOING_ID)
            return
        }
        when (intent.action) {
            ACTION_TOGGLE -> TimerSession.togglePlayPause()
            ACTION_SKIP -> TimerSession.skipStep()
            ACTION_END -> TimerSession.stop()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.sereneflow.timer.action.TOGGLE"
        const val ACTION_SKIP = "com.sereneflow.timer.action.SKIP"
        const val ACTION_END = "com.sereneflow.timer.action.END"
    }
}
