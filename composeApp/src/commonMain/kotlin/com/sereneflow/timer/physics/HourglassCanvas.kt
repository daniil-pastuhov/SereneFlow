package com.sereneflow.timer.physics

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

// Profile samples per bulb, from the neck (0) to the cap (PROFILE_STEPS).
private const val PROFILE_STEPS = 64

// Share of one bulb's volume that the sand fills.
private const val SAND_FILL = 0.72f

private const val GRAIN_COUNT = 140
private const val STREAM_GRAINS = 26

/**
 * Half-width of a bulb at [s], the distance from the neck (0) to the cap (1), as a fraction of the
 * widest point: a narrow funnel that flares into a round shoulder and tapers slightly to the cap.
 */
private fun bulbProfile(s: Float, neck: Float): Float {
    val shoulder = 0.62f
    return if (s < shoulder) {
        neck + (1f - neck) * sin(s / shoulder * PI.toFloat() / 2f).pow(1.7f)
    } else {
        val t = (s - shoulder) / (1f - shoulder)
        1f - 0.14f * t * t
    }
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * An hourglass whose sand drains from the top bulb into the bottom one. Both sand levels follow
 * the bulb's volume (a solid of revolution), so the top level falls slowly while the bulb is wide
 * and quickly in the funnel, and the bottom pile rises under the stream as a cone.
 */
@Composable
fun HourglassCanvas(
    progress: () -> Float, // 0f (full at top) to 1f (drained to bottom)
    modifier: Modifier = Modifier,
    glassColor: Color = Color(0xFFB0BEC5),
    sandColor: Color = Color(0xFFE0B27A),
    frameColor: Color = Color(0xFF6D4C41)
) {
    val frameTime = rememberFrameTimeSeconds()

    Spacer(modifier.fillMaxSize().drawWithCache {
        val width = size.width
        val height = size.height
        val centerX = width * 0.5f
        val centerY = height * 0.5f

        // The glass is tall enough for the bulbs to show above and below the dial, wide enough to
        // frame it, and leaves room for the title and controls; on a wide window the height limits both.
        val glassHeight = minOf(height * 0.6f, width * 1.9f)
        val bulbLength = glassHeight * 0.5f
        val radius = minOf(width * 0.32f, glassHeight * 0.25f)
        val unit = radius / 130f
        val neckY = centerY
        val neckRatio = 0.045f

        // Half-widths and the volume from the neck up to each sample (∝ ∫ r² ds).
        val halfWidths = FloatArray(PROFILE_STEPS + 1) { radius * bulbProfile(it / PROFILE_STEPS.toFloat(), neckRatio) }
        val volumes = FloatArray(PROFILE_STEPS + 1)
        for (i in 1..PROFILE_STEPS) {
            val r0 = halfWidths[i - 1]
            val r1 = halfWidths[i]
            volumes[i] = volumes[i - 1] + (r0 * r0 + r0 * r1 + r1 * r1) / 3f
        }
        val bulbVolume = volumes[PROFILE_STEPS]
        val sandVolume = bulbVolume * SAND_FILL

        fun halfWidthAt(s: Float): Float {
            val x = s.coerceIn(0f, 1f) * PROFILE_STEPS
            val i = x.toInt().coerceAtMost(PROFILE_STEPS - 1)
            return halfWidths[i] + (halfWidths[i + 1] - halfWidths[i]) * (x - i)
        }

        // The distance from the neck that holds [volume] of sand, measured from the neck.
        fun levelForVolume(volume: Float): Float {
            if (volume <= 0f) return 0f
            for (i in 1..PROFILE_STEPS) {
                if (volumes[i] >= volume) {
                    val t = (volume - volumes[i - 1]) / (volumes[i] - volumes[i - 1])
                    return (i - 1 + t) / PROFILE_STEPS
                }
            }
            return 1f
        }

        val glassTop = neckY - bulbLength
        val glassBottom = neckY + bulbLength

        // Inner outline of the glass: right side down, left side up, through both bulbs.
        val glassPath = Path().apply {
            moveTo(centerX - halfWidths[PROFILE_STEPS], glassTop)
            lineTo(centerX + halfWidths[PROFILE_STEPS], glassTop)
            for (i in PROFILE_STEPS downTo 0) {
                lineTo(centerX + halfWidths[i], neckY - bulbLength * i / PROFILE_STEPS)
            }
            for (i in 0..PROFILE_STEPS) {
                lineTo(centerX + halfWidths[i], neckY + bulbLength * i / PROFILE_STEPS)
            }
            lineTo(centerX - halfWidths[PROFILE_STEPS], glassBottom)
            for (i in PROFILE_STEPS downTo 0) {
                lineTo(centerX - halfWidths[i], neckY + bulbLength * i / PROFILE_STEPS)
            }
            for (i in 0..PROFILE_STEPS) {
                lineTo(centerX - halfWidths[i], neckY - bulbLength * i / PROFILE_STEPS)
            }
            close()
        }

        // Caps and the posts between them.
        val capWidth = radius * 2.5f
        val capHeight = glassHeight * 0.035f
        val capCorner = CornerRadius(capHeight * 0.45f)
        val postX = radius * 1.12f
        val postWidth = 5f * unit
        val frameDark = lerp(frameColor, Color.Black, 0.25f)
        val frameLight = lerp(frameColor, Color.White, 0.25f)
        val capBrush = Brush.verticalGradient(listOf(frameLight, frameColor, frameDark))
        val postBrush = Brush.horizontalGradient(listOf(frameDark, frameLight, frameColor))

        // Sand shading: lighter in the middle, darker toward the curved glass.
        val sandLight = lerp(sandColor, Color.White, 0.18f)
        val sandDark = lerp(sandColor, Color.Black, 0.22f)
        val sandShade = Brush.horizontalGradient(
            0f to sandDark,
            0.35f to sandColor,
            0.5f to sandLight,
            0.65f to sandColor,
            1f to sandDark,
            startX = centerX - radius,
            endX = centerX + radius
        )
        val grainLight = lerp(sandColor, Color.White, 0.45f)
        val grainDark = lerp(sandColor, Color.Black, 0.4f)

        onDrawBehind {
            val progress = progress().coerceIn(0f, 1f)
            val seconds = frameTime.value
            val flowing = progress > 0f && progress < 1f

            // Posts behind the glass, then its faint body.
            for (side in listOf(-1f, 1f)) {
                drawRoundRect(
                    brush = postBrush,
                    topLeft = Offset(centerX + side * postX - postWidth / 2f, glassTop - capHeight * 0.5f),
                    size = Size(postWidth, glassHeight + capHeight),
                    cornerRadius = CornerRadius(postWidth / 2f)
                )
            }
            drawPath(glassPath, color = glassColor.copy(alpha = 0.06f))

            clipPath(glassPath) {
                // Top bulb: the level falls with the volume left; while it drains, a crater
                // forms over the neck.
                val topLevel = levelForVolume(sandVolume * (1f - progress))
                if (topLevel > 0.002f) {
                    val levelY = neckY - bulbLength * topLevel
                    val craterDepth = minOf(bulbLength * 0.09f, bulbLength * topLevel * 0.8f) *
                        smoothstep(0f, 0.04f, progress)
                    val craterWidth = halfWidthAt(topLevel) * 0.55f
                    val left = centerX - radius * 1.2f
                    val right = centerX + radius * 1.2f
                    val topSand = Path().apply {
                        moveTo(left, levelY)
                        var x = left
                        val step = 3f * unit
                        while (x <= right) {
                            val d = (x - centerX) / craterWidth
                            lineTo(x, levelY + craterDepth * exp(-d * d))
                            x += step
                        }
                        lineTo(right, neckY)
                        lineTo(left, neckY)
                        close()
                    }
                    drawPath(topSand, brush = sandShade)
                    clipPath(topSand) { drawGrains(this, 0, centerX, radius, glassTop, neckY, unit, grainLight, grainDark) }
                }

                // Bottom bulb: an even layer from the volume that fell, and a cone under the stream.
                val fallen = sandVolume * progress
                val bottomLevel = levelForVolume(bulbVolume - fallen)
                val layerY = neckY + bulbLength * bottomLevel
                val moundHeight = bulbLength * 0.2f * smoothstep(0f, 0.06f, progress)
                val moundHalf = halfWidthAt(bottomLevel) * 0.95f
                val peakY = layerY - moundHeight
                if (progress > 0f) {
                    val bottomSand = Path().apply {
                        moveTo(centerX - radius * 1.2f, glassBottom + 1f)
                        lineTo(centerX - radius * 1.2f, layerY)
                        lineTo(centerX - moundHalf, layerY)
                        cubicTo(
                            centerX - moundHalf * 0.55f, layerY,
                            centerX - moundHalf * 0.12f, peakY,
                            centerX, peakY
                        )
                        cubicTo(
                            centerX + moundHalf * 0.12f, peakY,
                            centerX + moundHalf * 0.55f, layerY,
                            centerX + moundHalf, layerY
                        )
                        lineTo(centerX + radius * 1.2f, layerY)
                        lineTo(centerX + radius * 1.2f, glassBottom + 1f)
                        close()
                    }
                    drawPath(bottomSand, brush = sandShade)
                    clipPath(bottomSand) { drawGrains(this, 1, centerX, radius, neckY, glassBottom, unit, grainLight, grainDark) }
                }

                // The stream: a thin thread from the neck to the peak, thinning as the top empties.
                if (flowing) {
                    val thin = smoothstep(1f, 0.97f, progress) * smoothstep(0f, 0.005f, progress)
                    val streamEnd = peakY
                    drawLine(
                        color = sandColor.copy(alpha = 0.9f * thin),
                        start = Offset(centerX, neckY),
                        end = Offset(centerX, streamEnd),
                        strokeWidth = 2.6f * unit * (0.4f + 0.6f * thin),
                        cap = StrokeCap.Round
                    )
                    val fallLength = (streamEnd - neckY).coerceAtLeast(1f)
                    for (i in 0 until STREAM_GRAINS) {
                        // Falling grains accelerate (s ∝ t²) and fan out a little as they fall.
                        val t = fract(hash01(i, 11) + seconds * (0.9f + 0.6f * hash01(i, 12)))
                        val fall = t * t
                        val spread = (hash01(i, 13) - 0.5f) * 3.2f * unit * (0.3f + fall)
                        drawCircle(
                            color = (if (i % 3 == 0) sandDark else sandColor).copy(alpha = thin),
                            radius = (0.8f + hash01(i, 14) * 0.9f) * unit,
                            center = Offset(centerX + spread, neckY + fallLength * fall)
                        )
                    }
                    // A few grains bounce off the peak and roll down its sides.
                    for (i in 0 until 8) {
                        val t = fract(hash01(i, 21) + seconds * (1.2f + hash01(i, 22)))
                        val side = if (i % 2 == 0) -1f else 1f
                        val dx = side * moundHalf * 0.45f * t
                        val dy = moundHeight * 0.9f * t - 5f * unit * sin(t * PI.toFloat()) * (1f - t)
                        drawCircle(
                            color = sandColor.copy(alpha = thin * (1f - t)),
                            radius = 1.1f * unit,
                            center = Offset(centerX + dx, peakY + dy)
                        )
                    }
                }

                // Glass: shading toward the edges, and a reflection down the left of each bulb.
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.10f),
                        0.3f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.12f),
                        startX = centerX - radius,
                        endX = centerX + radius
                    ),
                    topLeft = Offset(centerX - radius * 1.2f, glassTop),
                    size = Size(radius * 2.4f, glassHeight)
                )
                for (sign in listOf(-1f, 1f)) {
                    val highlight = Path().apply {
                        for (i in 0..PROFILE_STEPS) {
                            val s = i / PROFILE_STEPS.toFloat()
                            if (s < 0.2f || s > 0.92f) continue
                            val x = centerX - halfWidths[i] * 0.72f
                            val y = neckY + sign * bulbLength * s
                            if (isEmpty) moveTo(x, y) else lineTo(x, y)
                        }
                    }
                    drawPath(
                        highlight,
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0f),
                            0.5f to Color.White.copy(alpha = 0.55f),
                            1f to Color.White.copy(alpha = 0f),
                            startY = neckY + sign * bulbLength * 0.2f,
                            endY = neckY + sign * bulbLength * 0.92f
                        ),
                        style = Stroke(width = 5f * unit, cap = StrokeCap.Round)
                    )
                }
            }

            // Glass outline, then the caps over its ends.
            drawPath(glassPath, color = glassColor.copy(alpha = 0.35f), style = Stroke(width = 2.2f * unit))
            drawPath(glassPath, color = Color.White.copy(alpha = 0.35f), style = Stroke(width = 0.8f * unit))
            for (capY in listOf(glassTop - capHeight, glassBottom)) {
                drawRoundRect(
                    brush = capBrush,
                    topLeft = Offset(centerX - capWidth / 2f, capY),
                    size = Size(capWidth, capHeight),
                    cornerRadius = capCorner
                )
            }
        }
    })
}

/** Fixed grains scattered over a bulb, so the sand reads as grains rather than a flat fill. */
private fun drawGrains(
    scope: DrawScope,
    salt: Int,
    centerX: Float,
    radius: Float,
    top: Float,
    bottom: Float,
    unit: Float,
    light: Color,
    dark: Color
) {
    for (i in 0 until GRAIN_COUNT) {
        val x = centerX + (hash01(i, salt * 7 + 1) * 2f - 1f) * radius
        val y = top + hash01(i, salt * 7 + 2) * (bottom - top)
        scope.drawCircle(
            color = (if (hash01(i, salt * 7 + 3) < 0.5f) light else dark).copy(alpha = 0.35f + 0.3f * hash01(i, salt * 7 + 4)),
            radius = (0.6f + hash01(i, salt * 7 + 5) * 0.9f) * unit,
            center = Offset(x, y)
        )
    }
}
