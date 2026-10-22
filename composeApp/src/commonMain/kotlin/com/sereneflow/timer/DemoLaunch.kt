package com.sereneflow.timer

/**
 * Opens a screen straight away instead of the routine list, for the store screenshot scripts
 * (scripts/ios_screenshots.sh, scripts/android_screenshots.sh). Only debug builds read it: from
 * launch arguments on iOS (MainViewController) and intent extras on Android (SereneFlowActivity).
 */
sealed class DemoLaunch {
    /** Runs [routineId], [elapsedSeconds] into its first step. */
    data class Timer(val routineId: String, val elapsedSeconds: Int) : DemoLaunch()
    data class Editor(val routineId: String) : DemoLaunch()
    data object Settings : DemoLaunch()

    companion object {
        /** `screen` is "timer", "editor" or "settings"; anything else opens the routine list. */
        fun parse(screen: String?, routineId: String?, elapsedSeconds: Int?): DemoLaunch? = when (screen) {
            "timer" -> routineId?.let { Timer(it, elapsedSeconds ?: 0) }
            "editor" -> routineId?.let { Editor(it) }
            "settings" -> Settings
            else -> null
        }
    }
}
