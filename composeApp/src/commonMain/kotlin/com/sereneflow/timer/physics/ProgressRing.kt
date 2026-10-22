package com.sereneflow.timer.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Thick, high-contrast progress ring that reads from across a room: a dim full-circle track with
 * a filled arc starting at 12 o'clock and running clockwise.
 *
 * @return the position of the arc's leading end, for drawing an indicator on it.
 */
internal fun DrawScope.drawProgressRing(
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    progress: Float,
    trackColor: Color,
    fillColor: Color
): Offset {
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)

    drawCircle(color = trackColor, radius = radius, center = center, style = Stroke(width = strokeWidth))
    val sweep = progress.coerceIn(0f, 1f) * 360f
    if (sweep > 0.1f) {
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

    val endRad = (-90f + sweep) * (PI.toFloat() / 180f)
    return Offset(center.x + radius * cos(endRad), center.y + radius * sin(endRad))
}
