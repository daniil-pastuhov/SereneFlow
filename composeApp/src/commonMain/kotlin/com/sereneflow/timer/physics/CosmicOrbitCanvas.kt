package com.sereneflow.timer.physics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class StarParticle(
    val startAngle: Float,
    val distanceRatio: Float,
    val angularSpeed: Float, // radians per second
    val radius: Float,
    val baseAlpha: Float,
    val twinkleFreq: Float,
    val useAccent: Boolean
)

/**
 * Step progress as a planet travelling once around a large orbit. The orbit itself is a thick
 * progress ring, so how much of the step has passed is readable at a glance from a distance.
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

    val stars = remember {
        List(36) {
            StarParticle(
                startAngle = Random.nextFloat() * 2f * PI.toFloat(),
                distanceRatio = Random.nextFloat() * 0.5f + 0.55f,
                angularSpeed = (Random.nextFloat() * 0.25f + 0.08f) * (if (Random.nextBoolean()) 1f else -1f),
                radius = Random.nextFloat() * 2.2f + 1f,
                baseAlpha = Random.nextFloat() * 0.3f + 0.15f,
                twinkleFreq = Random.nextFloat() * 1.5f + 0.5f,
                useAccent = Random.nextBoolean()
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val seconds = frameTime.value
        val progress = progress().coerceIn(0f, 1f)
        val minDim = minOf(size.width, size.height)
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val orbitRadius = minDim * 0.42f
        val ringStroke = minDim * 0.05f

        // Faint stardust drifting behind everything.
        for (star in stars) {
            val angle = star.startAngle + seconds * star.angularSpeed
            val r = orbitRadius * star.distanceRatio
            val twinkle = 0.7f + 0.3f * sin(seconds * star.twinkleFreq * 2f * PI.toFloat() + star.startAngle)
            drawCircle(
                color = (if (star.useAccent) accentColor else coreColor).copy(alpha = star.baseAlpha * twinkle),
                radius = star.radius,
                center = Offset(center.x + r * cos(angle), center.y + r * sin(angle))
            )
        }

        // Decorative inner orbit with a small moon, outside the countdown digits and kept subtle
        // so it doesn't compete with the progress ring.
        val innerRadius = orbitRadius * 0.74f
        drawCircle(
            color = trackColor,
            radius = innerRadius,
            center = center,
            style = Stroke(width = minDim * 0.004f)
        )
        val moonAngle = seconds * 2f * PI.toFloat() / 12f
        drawCircle(
            color = accentColor.copy(alpha = 0.55f),
            radius = minDim * 0.014f,
            center = Offset(center.x + innerRadius * cos(moonAngle), center.y + innerRadius * sin(moonAngle))
        )

        // Celestial core: a soft glow behind the countdown that brightens as the step progresses.
        // No solid discs here, so the digits on top stay legible.
        val pulse = 1f + 0.06f * sin(seconds * 2f * PI.toFloat() / 3.5f)
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

        // The orbit is the progress ring; the planet rides its leading edge.
        val planetCenter = drawProgressRing(
            center = center,
            radius = orbitRadius,
            strokeWidth = ringStroke,
            progress = progress,
            trackColor = trackColor,
            fillColor = coreColor
        )
        val planetRadius = minDim * 0.04f
        drawCircle(color = accentColor.copy(alpha = 0.30f), radius = planetRadius * 1.9f, center = planetCenter)
        drawCircle(color = accentColor, radius = planetRadius, center = planetCenter)
        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = planetRadius * 0.4f, center = planetCenter)
    }
}
