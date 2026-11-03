package com.sereneflow.timer.ui.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.sereneflow.timer.model.ReminderSchedule
import com.sereneflow.timer.resources.*
import org.jetbrains.compose.resources.stringResource

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
actual fun DurationPicker(
    initialSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var minutes by remember { mutableIntStateOf((initialSeconds / 60).coerceAtMost(MAX_PICKER_MINUTES)) }
    var seconds by remember { mutableIntStateOf(initialSeconds % 60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.picker_title)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberWheel(
                    value = minutes,
                    max = MAX_PICKER_MINUTES,
                    description = stringResource(Res.string.picker_minutes),
                    onChange = { minutes = it }
                )
                Text(stringResource(Res.string.picker_minutes_unit), modifier = Modifier.clearAndSetSemantics {})
                NumberWheel(
                    value = seconds,
                    max = 59,
                    description = stringResource(Res.string.picker_seconds),
                    onChange = { seconds = it }
                )
                Text(stringResource(Res.string.picker_seconds_unit), modifier = Modifier.clearAndSetSemantics {})
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickedDuration(minutes, seconds)) }) { Text(stringResource(Res.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun ReminderTimePicker(
    initial: ReminderSchedule,
    onConfirm: (ReminderSchedule) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reminder_picker_title)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(ReminderSchedule(state.hour, state.minute)) }) { Text(stringResource(Res.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        }
    )
}

/** The platform's own scrolling number wheel; [description] names it for TalkBack. */
@Composable
private fun NumberWheel(value: Int, max: Int, description: String, onChange: (Int) -> Unit) {
    // The wheel follows the activity's theme, not the app's; match the dialog's text instead.
    val textColor = LocalContentColor.current.toArgb()
    AndroidView(
        factory = { context ->
            NumberPicker(context).apply {
                minValue = 0
                maxValue = max
                this.value = value
                contentDescription = description
                setFormatter { "%02d".format(it) }
                setOnValueChangedListener { _, _, new -> onChange(new) }
            }
        },
        update = { picker ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) picker.textColor = textColor
        }
    )
}

@Composable
actual fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduce by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = read()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
        )
        reduce = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
actual fun SystemBarAppearance(darkBackground: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, darkBackground) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkBackground
                isAppearanceLightNavigationBars = !darkBackground
            }
        }
        onDispose {}
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

actual object PlatformIcons {
    actual val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    actual val More: ImageVector = Icons.Default.MoreVert
}
