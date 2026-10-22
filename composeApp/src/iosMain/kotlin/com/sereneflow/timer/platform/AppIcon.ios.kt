package com.sereneflow.timer.platform

import com.sereneflow.timer.model.AppThemeMode

/**
 * Implemented in Swift (`AppIconManager` in iosApp) and registered in [AppIconRegistry] at launch:
 * UIApplication's alternate-icon API is not in the Kotlin/Native UIKit bindings.
 */
interface AppIconBridge {
    val supportsAlternateIcons: Boolean

    /** The alternate icon's name, or null for the primary icon. */
    val alternateIconName: String?

    /** Calls [onDone] on the main thread once iOS has switched the icon, or failed to. */
    fun setAlternateIconName(name: String?, onDone: () -> Unit)
}

object AppIconRegistry {
    var bridge: AppIconBridge? = null
}

actual object AppIconSwitcher {
    actual val isSupported: Boolean
        get() = AppIconRegistry.bridge?.supportsAlternateIcons == true

    // iOS confirms every change with its own alert.
    actual val changeNote: String? = null

    actual fun current(): AppThemeMode {
        val name = AppIconRegistry.bridge?.alternateIconName
        return AppThemeMode.entries.firstOrNull { it.iosIconName == name } ?: AppThemeMode.ZEN_SAGE
    }

    // The change is asynchronous: the new name is only visible once iOS reports back.
    actual fun set(icon: AppThemeMode, onDone: (AppThemeMode) -> Unit) {
        val bridge = AppIconRegistry.bridge ?: return onDone(current())
        bridge.setAlternateIconName(icon.iosIconName) { onDone(current()) }
    }
}
