package com.sereneflow.timer.ui.platform

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

private val DestructiveRed = Color(0xFFE53935)

@Composable
actual fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) DestructiveRed else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

@Composable
actual fun ActionMenu(
    expanded: Boolean,
    title: String?,
    actions: List<MenuAction>,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        actions.forEach { action ->
            val tint = if (action.destructive) DestructiveRed else LocalContentColor.current
            DropdownMenuItem(
                text = { Text(action.title, color = if (action.destructive) DestructiveRed else Color.Unspecified) },
                leadingIcon = action.icon?.let { icon -> { Icon(icon, contentDescription = null, tint = tint) } },
                onClick = {
                    onDismiss()
                    action.onClick()
                }
            )
        }
    }
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

actual object PlatformIcons {
    actual val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    actual val More: ImageVector = Icons.Default.MoreVert
}
