package com.sereneflow.timer.physics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.random.Random

private class SandParticle(
    val offset: Float, // fall position at time 0: 0f at neck, 1f at bottom mound
    val horizontalJitter: Float,
    val speed: Float, // stream lengths per second
    val size: Float,
    val alpha: Float
)

@Composable
fun HourglassCanvas(
    progress: () -> Float, // 0f (full at top) to 1f (drained to bottom)
    modifier: Modifier = Modifier,
    glassColor: Color = Color(0xFFB0BEC5),
    sandColor: Color = Color(0xFFFFB74D),
    accentColor: Color = Color(0xFFFF9800)
) {
    val frameTime = rememberFrameTimeSeconds()

    val particles = remember {
        List(40) {
            SandParticle(
                offset = Random.nextFloat(),
                horizontalJitter = (Random.nextFloat() - 0.5f) * 16f,
                speed = Random.nextFloat() * 1.5f + 0.9f,
                size = Random.nextFloat() * 2.5f + 1.5f,
                alpha = Random.nextFloat() * 0.5f + 0.5f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val progress = progress().coerceIn(0f, 1f)
        val seconds = frameTime.value

        val centerX = width * 0.5f
        val glassTop = height * 0.15f
        val glassBottom = height * 0.85f
        val glassHeight = glassBottom - glassTop
        val glassWidth = minOf(width * 0.55f, 260f)
        val neckY = glassTop + glassHeight * 0.5f
        val neckWidth = 24f

        // Draw Outer Hourglass Glass Silhouette
        val glassPath = Path().apply {
            // Top rim
            moveTo(centerX - glassWidth * 0.5f, glassTop)
            lineTo(centerX + glassWidth * 0.5f, glassTop)
            // Right curve to neck
            cubicTo(
                centerX + glassWidth * 0.5f, glassTop + glassHeight * 0.3f,
                centerX + neckWidth * 0.5f, neckY - glassHeight * 0.1f,
                centerX + neckWidth * 0.5f, neckY
            )
            // Right curve from neck to bottom
            cubicTo(
                centerX + neckWidth * 0.5f, neckY + glassHeight * 0.1f,
                centerX + glassWidth * 0.5f, glassBottom - glassHeight * 0.3f,
                centerX + glassWidth * 0.5f, glassBottom
            )
            // Bottom rim
            lineTo(centerX - glassWidth * 0.5f, glassBottom)
            // Left curve from bottom to neck
            cubicTo(
                centerX - glassWidth * 0.5f, glassBottom - glassHeight * 0.3f,
                centerX - neckWidth * 0.5f, neckY + glassHeight * 0.1f,
                centerX - neckWidth * 0.5f, neckY
            )
            // Left curve from neck to top
            cubicTo(
                centerX - neckWidth * 0.5f, neckY - glassHeight * 0.1f,
                centerX - glassWidth * 0.5f, glassTop + glassHeight * 0.3f,
                centerX - glassWidth * 0.5f, glassTop
            )
            close()
        }

        // Hourglass translucent inner tint
        drawPath(
            path = glassPath,
            brush = Brush.verticalGradient(
                colors = listOf(glassColor.copy(alpha = 0.08f), glassColor.copy(alpha = 0.04f), glassColor.copy(alpha = 0.10f)),
                startY = glassTop,
                endY = glassBottom
            )
        )

        // Hourglass glass rim stroke
        drawPath(
            path = glassPath,
            color = glassColor.copy(alpha = 0.55f),
            style = Stroke(width = 3.5f)
        )

        // Top bulb sand volume (shrinks as progress goes 0f -> 1f)
        val topSandRatio = (1f - progress).coerceIn(0f, 1f)
        if (topSandRatio > 0.02f) {
            val topSandTop = glassTop + (glassHeight * 0.45f) * (1f - topSandRatio)
            val topSandWidth = glassWidth * (0.3f + 0.65f * topSandRatio)

            val topSandPath = Path().apply {
                moveTo(centerX - topSandWidth * 0.5f, topSandTop)
                lineTo(centerX + topSandWidth * 0.5f, topSandTop)
                quadraticTo(centerX + neckWidth * 0.4f, neckY - 5f, centerX, neckY)
                quadraticTo(centerX - neckWidth * 0.4f, neckY - 5f, centerX - topSandWidth * 0.5f, topSandTop)
                close()
            }

            drawPath(
                path = topSandPath,
                brush = Brush.verticalGradient(
                    colors = listOf(sandColor.copy(alpha = 0.90f), accentColor.copy(alpha = 0.95f)),
                    startY = topSandTop,
                    endY = neckY
                )
            )
        }

        // Bottom bulb sand mound (accumulates as progress goes 0f -> 1f)
        val bottomSandRatio = progress.coerceIn(0f, 1f)
        if (bottomSandRatio > 0.01f) {
            val moundHeight = (glassHeight * 0.45f) * bottomSandRatio
            val moundTopY = glassBottom - moundHeight
            val moundBaseWidth = glassWidth * (0.4f + 0.55f * bottomSandRatio)

            val bottomSandPath = Path().apply {
                moveTo(centerX - moundBaseWidth * 0.5f, glassBottom)
                // Conical mound peak
                quadraticTo(centerX - moundBaseWidth * 0.2f, moundTopY + 10f, centerX, moundTopY)
                quadraticTo(centerX + moundBaseWidth * 0.2f, moundTopY + 10f, centerX + moundBaseWidth * 0.5f, glassBottom)
                close()
            }

            drawPath(
                path = bottomSandPath,
                brush = Brush.verticalGradient(
                    colors = listOf(sandColor.copy(alpha = 0.95f), accentColor.copy(alpha = 0.90f)),
                    startY = moundTopY,
                    endY = glassBottom
                )
            )
        }

        // Active Falling Sand Stream (flowing while timer is running)
        if (progress < 0.99f) {
            // Main vertical stream line
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(sandColor, accentColor),
                    startY = neckY,
                    endY = glassBottom - (glassHeight * 0.45f * bottomSandRatio)
                ),
                start = Offset(centerX, neckY),
                end = Offset(centerX, glassBottom - (glassHeight * 0.45f * bottomSandRatio)),
                strokeWidth = 3f
            )

            // Dynamic scattered particles in stream
            val bottomTargetY = glassBottom - (glassHeight * 0.45f * bottomSandRatio)
            val streamHeight = (bottomTargetY - neckY).coerceAtLeast(10f)

            for (p in particles) {
                val fall = fract(p.offset + seconds * p.speed)
                val pY = neckY + streamHeight * fall
                val pX = centerX + p.horizontalJitter * (fall * 0.6f)

                drawCircle(
                    color = sandColor.copy(alpha = p.alpha),
                    radius = p.size,
                    center = Offset(pX, pY)
                )
            }
        }
    }
}
