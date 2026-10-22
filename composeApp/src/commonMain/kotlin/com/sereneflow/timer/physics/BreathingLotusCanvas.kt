package com.sereneflow.timer.physics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val PETAL_COUNT = 12

/**
 * Step progress as a lotus crown blooming petal by petal around the countdown. Each of the 12
 * petals stands for 1/12 of the step and sits under its matching segment of a thick progress
 * ring, so filled petals plus the ring show the elapsed share at a glance. A gentle 4-4-6-2 breath keeps it alive without
 * obscuring progress.
 */
@Composable
fun BreathingLotusCanvas(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    petalColor: Color = Color(0xFFF48FB1),
    goldAccent: Color = Color(0xFFFFD54F),
    trackColor: Color = petalColor.copy(alpha = 0.15f)
) {
    val frameTime = rememberFrameTimeSeconds()

    Canvas(modifier = modifier.fillMaxSize()) {
        val seconds = frameTime.value
        val progress = progress().coerceIn(0f, 1f)
        val minDim = minOf(size.width, size.height)
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val ringRadius = minDim * 0.42f
        val ringStroke = minDim * 0.05f

        val breath = breathPhase(seconds)
        val breathScale = 0.96f + 0.08f * breath

        // Soft aura that swells with the breath.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(petalColor.copy(alpha = 0.18f + 0.12f * breath), Color.Transparent),
                center = center,
                radius = ringRadius * 1.1f
            ),
            radius = ringRadius * 1.1f,
            center = center
        )

        // Petals form a crown between the countdown digits and the ring, leaving the centre clear.
        val petalBase = ringRadius * 0.58f
        val petalLength = ringRadius * 0.34f * breathScale
        val petalWidth = ringRadius * 0.16f * breathScale
        val outline = Stroke(width = minDim * 0.006f)
        val segmentDeg = 360f / PETAL_COUNT

        for (i in 0 until PETAL_COUNT) {
            // Petal i is centred in the ring segment it represents, starting at 12 o'clock.
            val angleDeg = -90f + segmentDeg * (i + 0.5f)
            val fill = (progress * PETAL_COUNT - i).coerceIn(0f, 1f)

            // Unbloomed petals are outlines; the current one grows from the centre as it fills.
            drawPetal(center, petalBase, angleDeg, petalLength, petalWidth, outline, petalColor.copy(alpha = 0.45f))
            if (fill > 0f) {
                val growth = 0.35f + 0.65f * fill
                drawPetal(
                    center = center,
                    baseRadius = petalBase,
                    angleDeg = angleDeg,
                    length = petalLength * growth,
                    width = petalWidth * growth,
                    style = null,
                    color = petalColor.copy(alpha = 0.55f + 0.4f * fill)
                )
            }
        }

        val head = drawProgressRing(
            center = center,
            radius = ringRadius,
            strokeWidth = ringStroke,
            progress = progress,
            trackColor = trackColor,
            fillColor = goldAccent
        )
        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = ringStroke * 0.28f, center = head)
    }
}

/**
 * 16-second 4-4-6-2 breath: inhale 4s (0→1), hold 4s, exhale 6s (1→0), rest 2s.
 * Eased with a smoothstep so turning points are gentle.
 */
private fun breathPhase(seconds: Float): Float {
    val t = seconds % 16f
    val linear = when {
        t < 4f -> t / 4f
        t < 8f -> 1f
        t < 14f -> 1f - (t - 8f) / 6f
        else -> 0f
    }
    return linear * linear * (3f - 2f * linear)
}

/** Petal pointing outwards along [angleDeg], starting [baseRadius] away from [center]. */
private fun DrawScope.drawPetal(
    center: Offset,
    baseRadius: Float,
    angleDeg: Float,
    length: Float,
    width: Float,
    style: Stroke?,
    color: Color
) {
    val angleRad = angleDeg * (PI.toFloat() / 180f)
    val perpRad = angleRad + PI.toFloat() / 2f
    val dir = Offset(cos(angleRad), sin(angleRad))
    val base = center + dir * baseRadius
    val tip = base + dir * length
    val mid = base + dir * (length * 0.5f)
    val side = Offset(width * cos(perpRad), width * sin(perpRad))

    val path = Path().apply {
        moveTo(base.x, base.y)
        quadraticTo(mid.x + side.x, mid.y + side.y, tip.x, tip.y)
        quadraticTo(mid.x - side.x, mid.y - side.y, base.x, base.y)
        close()
    }
    if (style != null) drawPath(path, color = color, style = style) else drawPath(path, color = color)
}
