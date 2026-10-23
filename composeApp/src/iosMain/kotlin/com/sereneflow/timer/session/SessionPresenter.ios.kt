package com.sereneflow.timer.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Implemented in Swift by the iOS app (ActivityKit has no Objective-C API) and registered in
 * [LiveActivityRegistry] at launch. Times are seconds since 1970 so they map directly to `Date`.
 */
interface LiveActivityBridge {
    fun startOrUpdate(
        routineTitle: String,
        stepTitle: String,
        stepIndex: Int,
        stepCount: Int,
        nextStepTitle: String?,
        stepDurationSeconds: Double,
        stepEndEpochSeconds: Double,
        remainingSeconds: Double,
        isPaused: Boolean,
        isCompleted: Boolean,
        /** The routine's accent colour as ARGB, or 0 for the app's own. */
        accentArgb: Long,
        /** The current round of [rounds]; both 1 without rounds. */
        round: Int,
        rounds: Int
    )

    fun end()
}

object LiveActivityRegistry {
    var bridge: LiveActivityBridge? = null
}

/**
 * Mirrors the session to the Live Activity (Lock Screen + Dynamic Island) and, while the app is in
 * the background, posts a notification banner on each step change and on completion.
 */
private class IosSessionPresenter : SessionPresenter {
    private var lastStepIndex = -1
    private var lastCompleted = false

    override fun show(snapshot: SessionSnapshot) = onMain {
        LiveActivityRegistry.bridge?.startOrUpdate(
            routineTitle = snapshot.routineTitle,
            stepTitle = snapshot.stepTitle,
            stepIndex = snapshot.stepIndex,
            stepCount = snapshot.stepCount,
            nextStepTitle = snapshot.nextStepTitle,
            stepDurationSeconds = snapshot.stepDurationMs / 1000.0,
            stepEndEpochSeconds = (snapshot.stepEndEpochMs ?: 0L) / 1000.0,
            remainingSeconds = snapshot.stepRemainingMs / 1000.0,
            isPaused = snapshot.isPaused,
            isCompleted = snapshot.isCompleted,
            accentArgb = snapshot.accentArgb ?: 0L,
            round = snapshot.round,
            rounds = snapshot.rounds
        )

        val stepChanged = lastStepIndex != -1 && snapshot.stepIndex != lastStepIndex
        val justCompleted = snapshot.isCompleted && !lastCompleted
        lastStepIndex = snapshot.stepIndex
        lastCompleted = snapshot.isCompleted

        val inBackground = UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateActive
        if (!inBackground) return@onMain
        when {
            justCompleted -> postBanner("Routine complete", "You finished \"${snapshot.routineTitle}\".")
            stepChanged -> postBanner(
                snapshot.stepTitle,
                listOfNotNull(
                    snapshot.roundLabel,
                    "Step ${snapshot.stepIndex + 1} of ${snapshot.stepCount}",
                    snapshot.routineTitle
                ).joinToString(" · ")
            )
        }
    }

    override fun end() = onMain {
        LiveActivityRegistry.bridge?.end()
        UNUserNotificationCenter.currentNotificationCenter().removeAllDeliveredNotifications()
        lastStepIndex = -1
        lastCompleted = false
    }

    private fun postBanner(title: String, body: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            // No sound: the routine already plays its own bowl and voice cues.
        }
        val request = UNNotificationRequest.requestWithIdentifier("sereneflow.session", content, trigger = null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, withCompletionHandler = null)
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) { block() }
    }
}

actual fun createSessionPresenter(): SessionPresenter = IosSessionPresenter()

@Composable
actual fun rememberNotificationPermissionRequest(): () -> Unit = remember {
    {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound
        ) { _, _ -> }
    }
}
