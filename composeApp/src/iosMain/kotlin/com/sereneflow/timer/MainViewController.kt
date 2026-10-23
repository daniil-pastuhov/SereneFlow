package com.sereneflow.timer

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.sereneflow.timer.transfer.readRoutineFile
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi

fun MainViewController(): UIViewController = ComposeUIViewController {
    App(demoLaunch = remember { demoLaunchFromArguments() })
}

/** Called by the Swift app (onOpenURL) for a `.sereneflow` file opened with SereneFlow. */
fun openRoutineFile(url: NSURL) {
    readRoutineFile(url)?.let { ExternalActions.post(ExternalAction.ImportRoutines(it)) }
}

/**
 * Called by the Swift app for a `sereneflow://start?routine=<id>` link (widget, links). Only
 * start links: Siri controls the routine through App Intents, so a link from a web page or
 * another app can't end or skip it.
 */
fun openAppUrl(url: String) {
    (ExternalAction.parseUrl(url) as? ExternalAction.StartRoutine)?.let(ExternalActions::post)
}

/** Called by the Swift SceneDelegate for a Home Screen quick action. */
fun startRoutineFromShortcut(routineId: String) {
    ExternalActions.post(ExternalAction.StartRoutine(routineId))
}

/** `-demoScreen timer -demoRoutine <id> -demoElapsed <seconds> [-demoBackground 1]`; see [DemoLaunch]. */
@OptIn(ExperimentalNativeApi::class)
private fun demoLaunchFromArguments(): DemoLaunch? {
    if (!Platform.isDebugBinary) return null
    val arguments = NSProcessInfo.processInfo.arguments.map { it.toString() }
    fun value(name: String) = arguments.indexOf(name).takeIf { it >= 0 }?.let { arguments.getOrNull(it + 1) }
    return DemoLaunch.parse(
        value("-demoScreen"),
        value("-demoRoutine"),
        value("-demoElapsed")?.toIntOrNull(),
        backgroundMode = value("-demoBackground") == "1"
    )
}
