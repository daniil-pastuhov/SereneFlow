package com.sereneflow.timer.session

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat

/**
 * Foreground service that keeps the process alive while a routine is running, so the timer engine
 * keeps ticking and audio cues keep playing with the app in the background. It owns no timer state
 * itself; [AndroidSessionPresenter] hands it the notification to show.
 */
class TimerService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = pendingNotification
        if (notification == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, TimerNotifications.ONGOING_ID, notification, type)
        updateWakeLock()
        // If the system kills the process the session is gone, so don't restart an empty service.
        return START_NOT_STICKY
    }

    fun updateWakeLock() {
        if (wantsWakeLock) {
            if (wakeLock?.isHeld != true) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SereneFlow:routine").apply {
                    // Safety net in case the session is never ended; re-acquired on the next update.
                    acquire(WAKE_LOCK_TIMEOUT_MS)
                }
            }
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val WAKE_LOCK_TIMEOUT_MS = 3 * 60 * 60 * 1000L

        @Volatile
        internal var instance: TimerService? = null

        @Volatile
        internal var pendingNotification: Notification? = null

        /** True while the routine is ticking; paused routines let the CPU sleep. */
        @Volatile
        internal var wantsWakeLock: Boolean = false
    }
}
