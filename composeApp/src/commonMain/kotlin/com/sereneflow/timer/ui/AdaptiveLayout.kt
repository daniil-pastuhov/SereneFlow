package com.sereneflow.timer.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Widest a column of forms or text gets. Wider windows (iPad, an unfolded iPhone Duo, Android
 * tablets) centre it instead of stretching rows across the screen.
 */
val ReadableContentWidth = 680.dp

/**
 * Gives [content] the horizontal padding that keeps it at most [ReadableContentWidth] wide and
 * centred, and at least [minPadding] from the edges. Padding rather than a narrower list, so the
 * whole window still scrolls. Sized from the space the screen has, never the device or
 * orientation: folding an iPhone Duo or resizing a window changes it mid-session.
 */
@Composable
fun ReadableWidth(
    modifier: Modifier = Modifier,
    minPadding: Dp = 20.dp,
    content: @Composable (horizontalPadding: Dp) -> Unit
) {
    BoxWithConstraints(modifier) {
        content(maxOf(minPadding, (maxWidth - ReadableContentWidth) / 2))
    }
}
