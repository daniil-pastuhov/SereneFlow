package com.sereneflow.timer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.sereneflow.timer.physics.rememberFrameTimeSeconds

/**
 * The timer screen's animated backdrop, recorded into a layer every frame so that UI on top of
 * it can show it blurred, like frosted glass ([frostedBackdrop]).
 */
@Stable
class BackdropCapture internal constructor(internal val layer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? = null
}

@Composable
fun rememberBackdropCapture(): BackdropCapture {
    val layer = rememberGraphicsLayer()
    return remember(layer) { BackdropCapture(layer) }
}

/** Draws this content as usual and records it into [capture] for [frostedBackdrop]. */
fun Modifier.captureBackdrop(capture: BackdropCapture): Modifier = this
    .onPlaced { capture.coordinates = it }
    .drawWithContent {
        capture.layer.record { this@drawWithContent.drawContent() }
        drawLayer(capture.layer)
    }

/**
 * Draws the part of [capture] behind this element, blurred by [radius], under its content. Clip
 * the element first to give the glass its shape, and give it an opaque background underneath if
 * the captured content is transparent anywhere, or the sharp original shows through. Where the
 * platform can't blur (Android before 12) the backdrop shows unblurred, so put a translucent
 * veil on top as well.
 */
@Composable
fun Modifier.frostedBackdrop(capture: BackdropCapture, radius: Dp): Modifier {
    // The backdrop animates every frame, so the glass redraws every frame too.
    val frameTime = rememberFrameTimeSeconds()
    val own = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // One layer for the element's lifetime: a pulsing element changes size every frame, which
    // would make a size-keyed cache create a new layer every frame.
    val blurred = rememberGraphicsLayer()
    val px = with(LocalDensity.current) { radius.toPx() }
    blurred.renderEffect = remember(px) { BlurEffect(px, px, TileMode.Clamp) }
    return this
        .onPlaced { own[0] = it }
        .drawBehind {
            frameTime.value
            val backdrop = capture.coordinates
            val self = own[0]
            if (backdrop == null || self == null || !backdrop.isAttached || !self.isAttached) return@drawBehind
            val offset = backdrop.localPositionOf(self, Offset.Zero)
            blurred.record {
                translate(-offset.x, -offset.y) { drawLayer(capture.layer) }
            }
            drawLayer(blurred)
        }
}
