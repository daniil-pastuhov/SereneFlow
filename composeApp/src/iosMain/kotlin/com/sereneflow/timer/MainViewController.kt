package com.sereneflow.timer

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi

fun MainViewController(): UIViewController = ComposeUIViewController {
    App(demoLaunch = remember { demoLaunchFromArguments() })
}

/** `-demoScreen timer -demoRoutine <id> -demoElapsed <seconds>`; see [DemoLaunch]. */
@OptIn(ExperimentalNativeApi::class)
private fun demoLaunchFromArguments(): DemoLaunch? {
    if (!Platform.isDebugBinary) return null
    val arguments = NSProcessInfo.processInfo.arguments.map { it.toString() }
    fun value(name: String) = arguments.indexOf(name).takeIf { it >= 0 }?.let { arguments.getOrNull(it + 1) }
    return DemoLaunch.parse(value("-demoScreen"), value("-demoRoutine"), value("-demoElapsed")?.toIntOrNull())
}
