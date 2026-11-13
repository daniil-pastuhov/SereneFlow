package com.sereneflow.timer.platform

import android.os.Build

actual val rateAppUrl: String = "https://play.google.com/store/apps/details?id=com.sereneflow.timer"

actual fun appEnvironment(): AppEnvironment {
    val version = AndroidAppContext.appContext?.let { context ->
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    return AppEnvironment(appVersion = version ?: "?", osName = "Android", osVersion = Build.VERSION.RELEASE)
}
