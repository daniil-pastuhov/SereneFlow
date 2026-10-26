package com.sereneflow.timer.physics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAU = 2f * PI.toFloat()
private const val STAR_COUNT = 48
private const val SPARK_COUNT = 22
private const val TICK_COUNT = 60

/**
 * Step progress as a planet travelling once around a large orbit. The orbit itself is a thick
 * progress ring, so how much of the step has passed is readable at a glance from a distance;
 * a dial of ticks around it lights up as the planet passes them.
 */
@Composable
fun CosmicOrbitCanvas(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    coreColor: Color = Color(0xFF64B5F6),
    accentColor: Color = Color(0xFFBA68C8),
    trackColor: Color = coreColor.copy(alpha = 0.15f)
) {
    val frameTime = rememberFrameTimeSeconds()

    Canvas(modifier = modifier.fillMaxSize()) {
        val seconds = frameTime.value
        val progress = progress().coerceIn(0f, 1f)
        val minDim = minOf(size.width, size.height)
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val orbitRadius = minDim * 0.42f
        val ringStroke = minDim * 0.05f

        // Nebula: two soft clouds slowly circling each other behind everything.
        for (i in 0..1) {
            val angle = seconds * TAU / 90f + i * PI.toFloat()
            val cloudCenter = Offset(
                center.x + orbitRadius * 0.22f * cos(angle),
                center.y + orbitRadius * 0.22f * sin(angle)
            )
            val cloudRadius = orbitRadius * (0.8f + 0.06f * sin(seconds * TAU / 17f + i * 2f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf((if (i == 0) coreColor else accentColor).copy(alpha = 0.09f), Color.Transparent),
                    center = cloudCenter,
                    radius = cloudRadius
                ),
                radius = cloudRadius,
                center = cloudCenter
            )
        }

        // Stardust drifting and twinkling; the brightest stars flare into four-pointed sparkles.
        for (i in 0 until STAR_COUNT) {
            val direction = if (hash01(i, 1) < 0.5f) 1f else -1f
            val angle = hash01(i, 2) * TAU + seconds * (0.03f + 0.09f * hash01(i, 3)) * direction
            val r = orbitRadius * (0.5f + 0.65f * hash01(i, 4))
            val twinkle = 0.6f + 0.4f * sin(seconds * TAU * (0.2f + 0.5f * hash01(i, 5)) + hash01(i, 6) * TAU)
            val star = Offset(center.x + r * cos(angle), center.y + r * sin(angle))
            val color = if (hash01(i, 7) < 0.5f) accentColor else coreColor
            val starRadius = minDim * (0.002f + 0.004f * hash01(i, 8))
            drawCircle(
                color = color.copy(alpha = (0.15f + 0.35f * hash01(i, 9)) * twinkle),
                radius = starRadius,
                center = star
            )
            if (hash01(i, 10) > 0.82f) {
                val flare = starRadius * (2f + 4f * twinkle * twinkle)
                val flareColor = lerp(color, Color.White, 0.5f).copy(alpha = 0.45f * twinkle)
                val flareWidth = minDim * 0.0015f
                drawLine(flareColor, Offset(star.x - flare, star.y), Offset(star.x + flare, star.y), flareWidth, StrokeCap.Round)
                drawLine(flareColor, Offset(star.x, star.y - flare), Offset(star.x, star.y + flare), flareWidth, StrokeCap.Round)
            }
        }

        // Decorative inner orbit: a slowly turning dashed path with a small moon trailing light,
        // outside the countdown digits and kept subtle so it doesn't compete with the progress ring.
        val innerRadius = orbitRadius * 0.74f
        val dash = innerRadius * TAU / 72f
        drawCircle(
            color = trackColor,
            radius = innerRadius,
            center = center,
            style = Stroke(
                width = minDim * 0.004f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * 0.5f, dash * 0.5f), phase = seconds * dash * 0.4f)
            )
        )
        val moonDegrees = seconds * 360f / 12f
        drawTrail(center, innerRadius, moonDegrees, 50f, minDim * 0.012f, accentColor.copy(alpha = 0.35f))
        val moonRad = moonDegrees * PI.toFloat() / 180f
        drawCircle(
            color = accentColor.copy(alpha = 0.7f),
            radius = minDim * 0.014f,
            center = Offset(center.x + innerRadius * cos(moonRad), center.y + innerRadius * sin(moonRad))
        )

        // Celestial core: a soft glow behind the countdown that brightens as the step progresses.
        // No solid discs here, so the digits on top stay legible.
        val pulse = 1f + 0.06f * sin(seconds * TAU / 3.5f)
        val glowRadius = orbitRadius * (0.55f + 0.1f * progress) * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(coreColor.copy(alpha = 0.10f + 0.14f * progress), Color.Transparent),
                center = center,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = center
        )

        // Dial: ticks outside the orbit that light up once the planet has passed them.
        val tickInner = orbitRadius + ringStroke * 0.9f
        for (i in 0 until TICK_COUNT) {
            val fraction = i.toFloat() / TICK_COUNT
            val major = i % 5 == 0
            val angle = fraction * TAU - PI.toFloat() / 2f
            val tickOuter = tickInner + minDim * (if (major) 0.026f else 0.013f)
            val lit = fraction <= progress && progress > 0f
            drawLine(
                color = if (lit) coreColor.copy(alpha = if (major) 0.9f else 0.6f) else trackColor,
                start = Offset(center.x + tickInner * cos(angle), center.y + tickInner * sin(angle)),
                end = Offset(center.x + tickOuter * cos(angle), center.y + tickOuter * sin(angle)),
                strokeWidth = minDim * (if (major) 0.006f else 0.0035f),
                cap = StrokeCap.Round
            )
        }

        val sweepDegrees = progress * 360f
        val planetDegrees = sweepDegrees - 90f

        // A wide halo under the filled arc so it glows.
        if (sweepDegrees > 0.1f) {
            drawArc(
                color = coreColor.copy(alpha = 0.10f),
                startAngle = -90f,
                sweepAngle = sweepDegrees,
                useCenter = false,
                topLeft = Offset(center.x - orbitRadius, center.y - orbitRadius),
                size = Size(orbitRadius * 2f, orbitRadius * 2f),
                style = Stroke(width = ringStroke * 1.8f, cap = StrokeCap.Round)
            )
        }

        // The orbit is the progress ring, shading from the accent at its start to the core colour
        // at the planet; the gradient wraps back to its start colour so the round start cap matches.
        val arcStart = lerp(coreColor, accentColor, 0.6f)
        val planetCenter = drawProgressRing(
            center = center,
            radius = orbitRadius,
            strokeWidth = ringStroke,
            progress = progress,
            trackColor = trackColor,
            fillColor = coreColor,
            fillBrush = Brush.sweepGradient(
                0f to arcStart,
                progress to coreColor,
                1f to arcStart,
                center = center
            )
        )

        // Comet tail and sparks shed behind the planet; both shrink to nothing at the very start
        // so nothing trails back past 12 o'clock.
        val tailDegrees = min(sweepDegrees, 70f)
        drawTrail(center, orbitRadius, planetDegrees, tailDegrees, ringStroke * 0.7f, accentColor.copy(alpha = 0.55f))
        val planetRad = planetDegrees * PI.toFloat() / 180f
        val tailRad = tailDegrees * PI.toFloat() / 180f
        val sparkFade = min(1f, sweepDegrees / 20f)
        for (i in 0 until SPARK_COUNT) {
            val period = 1.2f + 1.4f * hash01(i, 21)
            val age = fract(hash01(i, 22) + seconds / period)
            val angle = planetRad - tailRad * age * (0.5f + 0.5f * hash01(i, 23))
            val drift = (hash01(i, 24) - 0.5f) * ringStroke * (0.4f + 2.4f * age)
            val r = orbitRadius + drift
            val fade = (1f - age) * (1f - age)
            drawCircle(
                color = lerp(accentColor, Color.White, hash01(i, 25) * 0.4f).copy(alpha = 0.85f * fade * sparkFade),
                radius = minDim * (0.003f + 0.005f * hash01(i, 26)) * (1f - 0.6f * age),
                center = Offset(center.x + r * cos(angle), center.y + r * sin(angle))
            )
        }

        drawPlanet(planetCenter, minDim * 0.04f, center, seconds, accentColor, lerp(accentColor, coreColor, 0.6f), minDim)
    }
}

/**
 * A ringed planet lit from the orbit's core: a breathing halo, a tilted ring passing behind and
 * in front of a shaded body.
 */
private fun DrawScope.drawPlanet(
    planet: Offset,
    radius: Float,
    sun: Offset,
    seconds: Float,
    color: Color,
    ringColor: Color,
    minDim: Float
) {
    val haloRadius = radius * (2.6f + 0.25f * sin(seconds * TAU / 2.4f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.12f), Color.Transparent),
            center = planet,
            radius = haloRadius
        ),
        radius = haloRadius,
        center = planet
    )

    val ringSize = Size(radius * 3.8f, radius * 1.2f)
    val ringTopLeft = Offset(planet.x - ringSize.width / 2f, planet.y - ringSize.height / 2f)
    val ringStroke = Stroke(width = minDim * 0.004f)
    val tilt = -22f + 6f * sin(seconds * TAU / 9f)
    rotate(degrees = tilt, pivot = planet) {
        drawArc(ringColor.copy(alpha = 0.6f), 180f, 180f, false, ringTopLeft, ringSize, style = ringStroke)
    }

    // Highlight on the side facing the core, as if the core were the planet's sun.
    val dx = sun.x - planet.x
    val dy = sun.y - planet.y
    val length = max(sqrt(dx * dx + dy * dy), 1f)
    val light = Offset(planet.x + dx / length * radius * 0.45f, planet.y + dy / length * radius * 0.45f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lerp(color, Color.White, 0.85f), color, lerp(color, Color.Black, 0.35f)),
            center = light,
            radius = radius * 1.5f
        ),
        radius = radius,
        center = planet
    )

    rotate(degrees = tilt, pivot = planet) {
        drawArc(ringColor, 0f, 180f, false, ringTopLeft, ringSize, style = ringStroke)
    }
}

/**
 * A fading streak along a circle of [radius], ending at [headDegrees] (Canvas angles, 0 at
 * 3 o'clock) and reaching [lengthDegrees] back, transparent at its tail.
 */
private fun DrawScope.drawTrail(
    center: Offset,
    radius: Float,
    headDegrees: Float,
    lengthDegrees: Float,
    width: Float,
    color: Color
) {
    if (lengthDegrees < 0.5f) return
    rotate(degrees = headDegrees - lengthDegrees, pivot = center) {
        drawArc(
            brush = Brush.sweepGradient(
                0f to Color.Transparent,
                lengthDegrees / 360f to color,
                1f to Color.Transparent,
                center = center
            ),
            startAngle = 0f,
            sweepAngle = lengthDegrees,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = width)
        )
    }
}
