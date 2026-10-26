package com.sereneflow.timer.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Thick, high-contrast progress ring that reads from across a room: a dim full-circle track with
 * a filled arc starting at 12 o'clock and running clockwise.
 *
 * [fillBrush], when given, replaces [fillColor]; it is laid out as a sweep gradient whose 0 is
 * 12 o'clock.
 *
 * @return the position of the arc's leading end, for drawing an indicator on it.
 */
internal fun DrawScope.drawProgressRing(
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    progress: Float,
    trackColor: Color,
    fillColor: Color,
    fillBrush: Brush? = null
): Offset {
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)

    drawCircle(color = trackColor, radius = radius, center = center, style = Stroke(width = strokeWidth))
    val sweep = progress.coerceIn(0f, 1f) * 360f
    if (sweep > 0.1f) {
        if (fillBrush != null) {
            // Sweep gradients start at 3 o'clock, so turn the canvas to put their start at 12.
            rotate(degrees = -90f, pivot = center) {
                drawArc(
                    brush = fillBrush,
                    startAngle = 0f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        } else {
            drawArc(
                color = fillColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }

    val endRad = (-90f + sweep) * (PI.toFloat() / 180f)
    return Offset(center.x + radius * cos(endRad), center.y + radius * sin(endRad))
}
