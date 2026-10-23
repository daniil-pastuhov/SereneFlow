package com.sereneflow.timer.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual val voiceAssistant: VoiceAssistant = VoiceAssistant.SIRI

// The Shortcuts app lists the App Shortcuts, where one can also go on the Action button.
actual val openVoiceCommandsApp: (() -> Unit)? = {
    NSURL.URLWithString("shortcuts://")?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any?>(), null) }
}
