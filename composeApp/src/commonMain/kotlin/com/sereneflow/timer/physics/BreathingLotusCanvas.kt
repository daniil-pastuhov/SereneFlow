package com.sereneflow.timer.physics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val PETAL_COUNT = 12
private const val RIPPLE_COUNT = 3
private const val RIPPLE_PERIOD_SECONDS = 9f

/**
 * Step progress as a lotus seen from above, opening petal by petal around the countdown. Each of
 * the 12 outer petals stands for 1/12 of the step, clockwise from 12 o'clock: it unfolds from a
 * pale, closed petal into a full, shaded one as its share passes, and the inner petal behind each
 * gap follows half a share later. Ripples spread on the water beneath, and a gentle 4-4-6-2 breath
 * and sway keep the flower alive without obscuring progress.
 */
@Composable
fun BreathingLotusCanvas(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    petalColor: Color = Color(0xFFF48FB1),
    waterColor: Color = Color(0xFF80CBC4)
) {
    val frameTime = rememberFrameTimeSeconds()

    Canvas(modifier = modifier.fillMaxSize()) {
        val seconds = frameTime.value
        val progress = progress().coerceIn(0f, 1f)
        val minDim = minOf(size.width, size.height)
        val center = Offset(size.width * 0.5f, size.height * 0.5f)

        val breath = breathPhase(seconds)
        val breathScale = 0.97f + 0.05f * breath

        // Ripples spreading from under the flower, fading as they widen.
        for (k in 0 until RIPPLE_COUNT) {
            val phase = (seconds / RIPPLE_PERIOD_SECONDS + k.toFloat() / RIPPLE_COUNT) % 1f
            val fade = sin(phase * PI.toFloat())
            drawCircle(
                color = waterColor.copy(alpha = 0.22f * fade * (1f - phase)),
                radius = minDim * (0.40f + 0.2f * phase),
                center = center,
                style = Stroke(width = minDim * (0.004f - 0.002f * phase))
            )
        }

        // Soft glow on the water that swells with the breath.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(petalColor.copy(alpha = 0.14f + 0.1f * breath), Color.Transparent),
                center = center,
                radius = minDim * 0.48f
            ),
            radius = minDim * 0.48f,
            center = center
        )

        val segmentDeg = 360f / PETAL_COUNT
        val shadow = Color.Black.copy(alpha = 0.07f)

        // Outer petals lie flat on the water; the inner ones, between them, stand more upright, so
        // they look shorter and narrower from above and are drawn over the outer petals' bases.
        val outer = PetalLayer(
            baseRadius = minDim * 0.2f,
            length = minDim * 0.25f * breathScale,
            halfWidth = minDim * 0.085f * breathScale,
            angleOffset = 0.5f,
            lag = 0f
        )
        val inner = PetalLayer(
            baseRadius = minDim * 0.17f,
            length = minDim * 0.19f * breathScale,
            halfWidth = minDim * 0.07f * breathScale,
            angleOffset = 1f,
            lag = 0.5f
        )

        for (layer in listOf(outer, inner)) {
            val petals = (0 until PETAL_COUNT).map { i ->
                // Petal i opens over its own share of the step, from closed (0) to open (1).
                val open = easeOut((progress * PETAL_COUNT - i - layer.lag).coerceIn(0f, 1f))
                val sway = 1.2f * sin(seconds * 0.5f + i * 1.7f + layer.lag * 3f) * (0.3f + 0.7f * open)
                val angleDeg = -90f + segmentDeg * (i + layer.angleOffset) + sway
                val shape = petalPath(
                    center = center,
                    baseRadius = layer.baseRadius,
                    angleDeg = angleDeg,
                    // A closed petal is folded up towards the centre: short and narrow from above.
                    length = layer.length * (0.6f + 0.4f * open),
                    halfWidth = layer.halfWidth * (0.45f + 0.55f * open)
                )
                shape to open
            }

            // Open petals cast a faint shadow on whatever lies below them.
            for ((shape, open) in petals) {
                if (open <= 0f) continue
                translate(top = minDim * 0.008f * open) {
                    drawPath(shape.path, color = shadow.copy(alpha = shadow.alpha * open))
                }
            }

            for ((shape, open) in petals) {
                drawPetal(shape, open, petalColor, minDim)
            }
        }
    }
}

private class PetalLayer(
    val baseRadius: Float,
    val length: Float,
    val halfWidth: Float,
    /** Where the petals sit, in segments from 12 o'clock: 0.5 centres them in their segment. */
    val angleOffset: Float,
    /** How many segments later than the outer petals this layer opens. */
    val lag: Float
)

private class PetalShape(
    val path: Path,
    val base: Offset,
    val tip: Offset,
    val dir: Offset,
    val side: Offset,
    val length: Float,
    val halfWidth: Float
)

/**
 * A lotus petal pointing outwards along [angleDeg] from [baseRadius] away from [center]: narrow at
 * the base, widest past the middle and drawn to a point at the tip.
 */
private fun petalPath(
    center: Offset,
    baseRadius: Float,
    angleDeg: Float,
    length: Float,
    halfWidth: Float
): PetalShape {
    val angleRad = angleDeg * (PI.toFloat() / 180f)
    val dir = Offset(cos(angleRad), sin(angleRad))
    val side = Offset(-dir.y, dir.x)
    val base = center + dir * baseRadius
    val tip = base + dir * length

    fun at(along: Float, across: Float) = base + dir * (length * along) + side * (halfWidth * across)

    val c1 = at(0.12f, 0.8f)
    val c2 = at(0.75f, 1.15f)
    val c3 = at(0.75f, -1.15f)
    val c4 = at(0.12f, -0.8f)
    val path = Path().apply {
        moveTo(base.x, base.y)
        cubicTo(c1.x, c1.y, c2.x, c2.y, tip.x, tip.y)
        cubicTo(c3.x, c3.y, c4.x, c4.y, base.x, base.y)
        close()
    }
    return PetalShape(path, base, tip, dir, side, length, halfWidth)
}

/**
 * Fills a petal pale at the base and deepening to [petalColor] at the tip, as lotus petals do,
 * with a darker rim and fine veins once it opens. A closed petal stays pale, the colour of a bud.
 */
private fun DrawScope.drawPetal(shape: PetalShape, open: Float, petalColor: Color, minDim: Float) {
    // Petals are near white at the base; the colour gathers towards the tip as the petal opens.
    val pale = lerp(petalColor, Color.White, 0.85f)
    val alpha = 0.85f + 0.15f * open
    drawPath(
        path = shape.path,
        brush = Brush.linearGradient(
            colors = listOf(
                pale.copy(alpha = alpha),
                lerp(pale, petalColor, 0.1f + 0.3f * open).copy(alpha = alpha),
                lerp(pale, petalColor, 0.25f + 0.65f * open).copy(alpha = alpha)
            ),
            start = shape.base,
            end = shape.tip
        )
    )
    drawPath(
        path = shape.path,
        color = petalColor.copy(alpha = 0.3f + 0.25f * open),
        style = Stroke(width = minDim * 0.003f)
    )
    if (open <= 0f) return

    // Midrib and two side veins, light lines running towards the tip.
    val vein = Color.White.copy(alpha = 0.22f * open)
    val veinStroke = Stroke(width = minDim * 0.0025f, cap = StrokeCap.Round)
    fun at(along: Float, across: Float) =
        shape.base + shape.dir * (shape.length * along) + shape.side * (shape.halfWidth * across)

    val midrib = Path().apply {
        val from = at(0.08f, 0f)
        val to = at(0.86f, 0f)
        moveTo(from.x, from.y)
        lineTo(to.x, to.y)
    }
    drawPath(midrib, color = vein, style = veinStroke)
    for (sideSign in listOf(1f, -1f)) {
        val from = at(0.12f, 0.1f * sideSign)
        val via = at(0.5f, 0.6f * sideSign)
        val to = at(0.82f, 0.28f * sideSign)
        val path = Path().apply {
            moveTo(from.x, from.y)
            quadraticTo(via.x, via.y, to.x, to.y)
        }
        drawPath(path, color = vein.copy(alpha = vein.alpha * 0.6f), style = veinStroke)
    }
}

private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

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
