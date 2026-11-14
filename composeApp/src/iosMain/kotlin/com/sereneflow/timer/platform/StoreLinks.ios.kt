package com.sereneflow.timer.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

/** The App Store's numeric Apple ID for SereneFlow (App Store Connect → App Information). */
private const val APP_STORE_ID = "6825780663"

actual val rateAppUrl: String = "https://apps.apple.com/app/id$APP_STORE_ID?action=write-review"

/**
 * Implemented in Swift (`StoreReviewManager` in iosApp) and registered in [StoreReviewRegistry] at
 * launch: StoreKit's review request is a Swift-only API since iOS 16.
 */
interface StoreReviewBridge {
    fun requestReview()
}

object StoreReviewRegistry {
    var bridge: StoreReviewBridge? = null
}

@Composable
actual fun rememberStoreReviewRequest(): () -> Unit = { StoreReviewRegistry.bridge?.requestReview() }

actual fun appEnvironment(): AppEnvironment = AppEnvironment(
    appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?",
    osName = UIDevice.currentDevice.systemName,
    osVersion = UIDevice.currentDevice.systemVersion
)
