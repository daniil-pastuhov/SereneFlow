package com.sereneflow.timer.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

data class MenuAction(
    val title: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Confirmation dialog shown while this composable is in the composition.
 * iOS presents a native `UIAlertController`; Android uses a Material 3 `AlertDialog`.
 */
@Composable
expect fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel"
)

/**
 * Contextual action menu. Place it inside the same `Box` as its anchor button.
 * iOS presents a native action sheet; Android shows a `DropdownMenu` anchored to the parent.
 */
@Composable
expect fun ActionMenu(
    expanded: Boolean,
    title: String?,
    actions: List<MenuAction>,
    onDismiss: () -> Unit
)

/**
 * Picks a step duration with minutes and seconds wheels, shown while this composable is in the
 * composition. iOS presents a `UIPickerView` in a sheet; Android `NumberPicker`s in a dialog.
 * [onConfirm] gets the total in seconds, at least [MIN_STEP_SECONDS].
 */
@Composable
expect fun DurationPicker(
    initialSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
)

const val MIN_STEP_SECONDS = 10
const val MAX_PICKER_MINUTES = 180

/** The duration the wheels show, as seconds no shorter than a step may be. */
internal fun pickedDuration(minutes: Int, seconds: Int): Int = (minutes * 60 + seconds).coerceAtLeast(MIN_STEP_SECONDS)

/** Keeps the display from sleeping while composed with [enabled] = true. */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

/**
 * Light status and navigation bar icons over a dark background, dark ones over a light one. The
 * app's theme decides, not the system's dark mode: Midnight Starlight is dark in light mode.
 */
@Composable
expect fun SystemBarAppearance(darkBackground: Boolean)

/** Icons that differ between the platforms' native conventions. */
expect object PlatformIcons {
    val Back: ImageVector
    val More: ImageVector
}
