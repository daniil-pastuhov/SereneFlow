package com.sereneflow.timer.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import com.sereneflow.timer.model.AppThemeMode
import com.sereneflow.timer.resources.Res
import com.sereneflow.timer.resources.settings_icon_note_android
import org.jetbrains.compose.resources.StringResource
import com.sereneflow.timer.session.TimerSession

actual object AppIconSwitcher {
    actual val isSupported: Boolean = true

    // Launchers drop a home-screen shortcut whose component is disabled.
    actual val changeNote: StringResource? = Res.string.settings_icon_note_android

    /**
     * Chosen but not applied yet. Disabling the launcher alias the app was started through closes
     * its task, so the switch waits until the app is no longer on screen ([applyPendingIcon]).
     */
    private var pending: AppThemeMode? = null

    actual fun current(): AppThemeMode = pending ?: enabledIcon()

    actual fun set(icon: AppThemeMode, onDone: (AppThemeMode) -> Unit) {
        pending = icon.takeIf { it != enabledIcon() }
        onDone(current())
    }

    /**
     * Switches to the pending icon. Called when the activity stops, which is also what happens when
     * the screen locks; the switch is held back then, and while a routine runs in the background,
     * as both still need the task, and applied at a later stop instead.
     */
    fun applyPendingIcon() {
        val icon = pending ?: return
        val context = AndroidAppContext.appContext ?: return
        if (TimerSession.active.value != null) return
        if (context.getSystemService(PowerManager::class.java)?.isInteractive == false) return
        pending = null
        val packageManager = context.packageManager
        // Enable the new alias before disabling the old one, so the app always has a launcher entry.
        val ordered = listOf(icon) + AppThemeMode.entries.filter { it != icon }
        for (alias in ordered) {
            packageManager.setComponentEnabledSetting(
                ComponentName(context, alias.androidLauncherAlias),
                if (alias == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun enabledIcon(): AppThemeMode {
        val context: Context = AndroidAppContext.appContext ?: return AppThemeMode.ZEN_SAGE
        val packageManager = context.packageManager
        return AppThemeMode.entries.firstOrNull { icon ->
            when (packageManager.getComponentEnabledSetting(ComponentName(context, icon.androidLauncherAlias))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                // Only the default alias is enabled in the manifest.
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppThemeMode.ZEN_SAGE
                else -> false
            }
        } ?: AppThemeMode.ZEN_SAGE
    }
}
