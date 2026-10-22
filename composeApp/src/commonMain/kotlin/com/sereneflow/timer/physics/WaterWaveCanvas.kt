package com.sereneflow.timer.physics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private class WaterBubble(
    val xRatio: Float,
    val cycleOffset: Float,
    val radius: Float,
    val riseSpeed: Float, // screen heights per second
    val swayFreq: Float,
    val swayAmp: Float,
    val baseAlpha: Float,
    val phase: Float
)

private const val BUBBLE_TRAVEL = 1.1f

@Composable
fun WaterWaveCanvas(
    progress: () -> Float, // 0f (empty) to 1f (full) or draining
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF1E88E5),
    secondaryColor: Color = Color(0xFF4DD0E1)
) {
    val frameTime = rememberFrameTimeSeconds()

    val bubbles = remember {
        List(28) {
            WaterBubble(
                xRatio = Random.nextFloat(),
                cycleOffset = Random.nextFloat() * BUBBLE_TRAVEL,
                radius = Random.nextFloat() * 7f + 3f,
                riseSpeed = Random.nextFloat() * 0.18f + 0.09f,
                swayFreq = Random.nextFloat() * 2f + 1f,
                swayAmp = Random.nextFloat() * 12f + 4f,
                baseAlpha = Random.nextFloat() * 0.45f + 0.25f,
                phase = Random.nextFloat() * 2f * PI.toFloat()
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val seconds = frameTime.value
        // One full wave cycle every 4 seconds, continuous (no wrap-around jump).
        val time = seconds * 2f * PI.toFloat() / 4f

        // Current water level: 1f progress = full (top), 0f = empty (bottom)
        val clampedProgress = progress().coerceIn(0.02f, 0.98f)
        val waterLevelY = height * (1f - clampedProgress)

        // Wave 1: Primary fluid crest
        val wavePath1 = Path()
        wavePath1.moveTo(0f, height)
        wavePath1.lineTo(0f, waterLevelY)

        val waveAmp1 = 18f
        val waveFreq1 = 1.3f
        val step = 8f

        var x = 0f
        while (x <= width) {
            val normX = x / width
            val y = waterLevelY + waveAmp1 * sin(normX * 2f * PI.toFloat() * waveFreq1 + time)
            wavePath1.lineTo(x, y)
            x += step
        }
        wavePath1.lineTo(width, height)
        wavePath1.close()

        // Wave 2: Secondary fluid crest with slight phase delay and transparency
        val wavePath2 = Path()
        wavePath2.moveTo(0f, height)
        wavePath2.lineTo(0f, waterLevelY)

        val waveAmp2 = 14f
        val waveFreq2 = 1.8f

        x = 0f
        while (x <= width) {
            val normX = x / width
            val y = waterLevelY + waveAmp2 * sin(normX * 2f * PI.toFloat() * waveFreq2 - time * 0.8f + 1.2f)
            wavePath2.lineTo(x, y)
            x += step
        }
        wavePath2.lineTo(width, height)
        wavePath2.close()

        // Draw background wave
        drawPath(
            path = wavePath2,
            brush = Brush.verticalGradient(
                colors = listOf(
                    secondaryColor.copy(alpha = 0.45f),
                    primaryColor.copy(alpha = 0.55f),
                    primaryColor.copy(alpha = 0.75f)
                ),
                startY = waterLevelY - 20f,
                endY = height
            )
        )

        // Draw foreground wave
        drawPath(
            path = wavePath1,
            brush = Brush.verticalGradient(
                colors = listOf(
                    secondaryColor.copy(alpha = 0.85f),
                    primaryColor.copy(alpha = 0.90f),
                    primaryColor.copy(alpha = 0.98f)
                ),
                startY = waterLevelY - 20f,
                endY = height
            )
        )

        // Wave crest highlight line
        drawWaveCrestHighlight(width, waterLevelY, waveAmp1, waveFreq1, time, secondaryColor)

        // Draw buoyant rising bubbles inside the water body
        for ((index, bubble) in bubbles.withIndex()) {
            // Position is a pure function of time: rise from below the floor, loop back once past the top.
            val travelled = bubble.cycleOffset + seconds * bubble.riseSpeed
            val cycle = (travelled / BUBBLE_TRAVEL).toInt()
            val yRatio = 1.05f - (travelled % BUBBLE_TRAVEL)
            // Each loop starts at a new horizontal position.
            val xRatio = fract(bubble.xRatio + hash01(index, cycle))

            val bubbleCurrentY = yRatio * height
            if (bubbleCurrentY >= waterLevelY && bubbleCurrentY <= height) {
                val swayX = bubble.swayAmp * sin(yRatio * 2f * PI.toFloat() * bubble.swayFreq + bubble.phase + time)
                val bubbleCurrentX = (xRatio * width + swayX).coerceIn(0f, width)

                // Depth fade
                val distFromSurface = (bubbleCurrentY - waterLevelY).coerceAtLeast(0f)
                val surfaceFade = (distFromSurface / 60f).coerceIn(0.2f, 1f)
                val alpha = bubble.baseAlpha * surfaceFade

                // Bubble outer glow / rim
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = bubble.radius,
                    center = Offset(bubbleCurrentX, bubbleCurrentY)
                )

                // Bubble specular dot
                drawCircle(
                    color = Color.White.copy(alpha = (alpha * 1.5f).coerceAtMost(0.9f)),
                    radius = bubble.radius * 0.35f,
                    center = Offset(bubbleCurrentX - bubble.radius * 0.3f, bubbleCurrentY - bubble.radius * 0.3f)
                )
            }
        }
    }
}

private fun DrawScope.drawWaveCrestHighlight(
    width: Float,
    waterLevelY: Float,
    waveAmp: Float,
    waveFreq: Float,
    time: Float,
    highlightColor: Color
) {
    val crestPath = Path()
    var first = true
    var x = 0f
    val step = 8f
    while (x <= width) {
        val normX = x / width
        val y = waterLevelY + waveAmp * sin(normX * 2f * PI.toFloat() * waveFreq + time)
        if (first) {
            crestPath.moveTo(x, y)
            first = false
        } else {
            crestPath.lineTo(x, y)
        }
        x += step
    }

    drawPath(
        path = crestPath,
        color = highlightColor.copy(alpha = 0.5f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
    )
}
