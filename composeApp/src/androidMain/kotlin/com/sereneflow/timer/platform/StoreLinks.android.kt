package com.sereneflow.timer.platform

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.android.play.core.review.ReviewManagerFactory

actual val rateAppUrl: String = "https://play.google.com/store/apps/details?id=com.sereneflow.timer"

@Composable
actual fun rememberStoreReviewRequest(): () -> Unit {
    val activity = LocalActivity.current
    return remember(activity) {
        request@{
            if (activity == null) return@request
            val manager = ReviewManagerFactory.create(activity)
            // Fails without the Play Store (a sideloaded build, an emulator without it): then nothing shows.
            manager.requestReviewFlow().addOnCompleteListener { request ->
                if (request.isSuccessful && !activity.isFinishing && !activity.isDestroyed) {
                    manager.launchReviewFlow(activity, request.result)
                }
            }
        }
    }
}

actual fun appEnvironment(): AppEnvironment {
    val version = AndroidAppContext.appContext?.let { context ->
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    return AppEnvironment(appVersion = version ?: "?", osName = "Android", osVersion = Build.VERSION.RELEASE)
}
