package com.sereneflow.timer.platform

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

/** The App Store's numeric Apple ID for SereneFlow (App Store Connect → App Information). */
private const val APP_STORE_ID = "6825780663"

actual val rateAppUrl: String = "https://apps.apple.com/app/id$APP_STORE_ID?action=write-review"

actual fun appEnvironment(): AppEnvironment = AppEnvironment(
    appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?",
    osName = UIDevice.currentDevice.systemName,
    osVersion = UIDevice.currentDevice.systemVersion
)
