package com.sereneflow.timer.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * The keys of text fields that still show placeholder text the editor filled in (a new routine's
 * name, its starter steps, "New Step N"). Such a field selects all of its text when focused, so
 * typing replaces it; it leaves the set on its first edit.
 */
@Stable
class UntouchedDefaults(initial: Set<String>) {
    private var keys by mutableStateOf(initial)

    operator fun contains(key: String): Boolean = key in keys

    fun add(key: String) {
        keys = keys + key
    }

    fun edited(key: String) {
        if (key in keys) keys = keys - key
    }
}

@Composable
fun rememberUntouchedDefaults(initial: () -> Set<String>): UntouchedDefaults = remember { UntouchedDefaults(initial()) }

/** An [OutlinedTextField] that selects all of its text when it gains focus, while [selectAllOnFocus]. */
@Composable
fun SelectAllOnFocusField(
    value: String,
    onValueChange: (String) -> Unit,
    selectAllOnFocus: Boolean,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    // Holds the selection; the text itself comes from [value], which may change from outside.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (fieldValue.text == value) fieldValue else TextFieldValue(value, TextRange(value.length))
    val currentText by rememberUpdatedState(value)
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(focused) {
        if (!focused || !selectAllOnFocus) return@LaunchedEffect
        // A tap places the cursor as it focuses the field; select on the next frame so this wins.
        withFrameNanos { }
        fieldValue = TextFieldValue(currentText, TextRange(0, currentText.length))
    }

    OutlinedTextField(
        value = shown,
        onValueChange = { newValue ->
            fieldValue = newValue
            if (newValue.text != value) onValueChange(newValue.text)
        },
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        label = label,
        singleLine = singleLine,
        shape = shape,
        colors = colors,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions
    )
}
