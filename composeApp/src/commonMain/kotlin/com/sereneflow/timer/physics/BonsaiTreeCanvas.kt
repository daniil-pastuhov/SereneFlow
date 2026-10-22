package com.sereneflow.timer.physics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class FallingPetal(
    val cycleOffset: Float,
    val size: Float,
    val fallSpeed: Float, // screen heights per second
    val driftFreq: Float,
    val driftAmp: Float,
    val baseAngle: Float,
    val rotationSpeed: Float,
    val color: Color
)

private const val PETAL_START_Y = 0.3f
private const val PETAL_FALL_RANGE = 0.65f

@Composable
fun BonsaiTreeCanvas(
    progress: () -> Float, // 0f to 1f (growing and blooming)
    modifier: Modifier = Modifier,
    trunkColor: Color = Color(0xFF4A3B32),
    foliageColor: Color = Color(0xFFE57373),
    leafColor: Color = Color(0xFF81C784)
) {
    val frameTime = rememberFrameTimeSeconds()

    val petals = remember {
        List(22) {
            FallingPetal(
                cycleOffset = Random.nextFloat() * PETAL_FALL_RANGE,
                size = Random.nextFloat() * 6f + 4f,
                fallSpeed = Random.nextFloat() * 0.06f + 0.04f,
                driftFreq = Random.nextFloat() * 2f + 1f,
                driftAmp = Random.nextFloat() * 15f + 5f,
                baseAngle = Random.nextFloat() * 360f,
                rotationSpeed = Random.nextFloat() * 3f + 1f,
                color = if (Random.nextBoolean()) foliageColor.copy(alpha = 0.8f) else leafColor.copy(alpha = 0.7f)
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val progress = progress().coerceIn(0f, 1f)
        // One gust cycle every 6 seconds, continuous.
        val seconds = frameTime.value
        val time = seconds * 2f * PI.toFloat() / 6f

        // Ground hill
        val groundY = height * 0.88f
        val groundPath = Path().apply {
            moveTo(0f, height)
            lineTo(0f, groundY + 15f)
            quadraticTo(width * 0.5f, groundY - 20f, width, groundY + 15f)
            lineTo(width, height)
            close()
        }
        drawPath(groundPath, color = trunkColor.copy(alpha = 0.15f))

        // Tree root position
        val rootX = width * 0.5f
        val rootY = groundY - 5f

        // Initial trunk length scaled by growth progress
        val growthFactor = 0.35f + 0.65f * progress
        val initialTrunkLength = height * 0.22f * growthFactor

        // Wind sway factor
        val wind = sin(time) * 0.05f

        // Recursive tree rendering
        drawBranch(
            startX = rootX,
            startY = rootY,
            length = initialTrunkLength,
            angleRad = -PI.toFloat() / 2f + wind * 0.4f,
            depth = 0,
            maxDepth = 6,
            progress = progress,
            windTime = time,
            trunkColor = trunkColor,
            foliageColor = foliageColor,
            leafColor = leafColor
        )

        // Draw and update falling petals
        for ((index, petal) in petals.withIndex()) {
            // Fall from the canopy to the ground, then restart from a new spot in the canopy.
            val fallen = petal.cycleOffset + seconds * petal.fallSpeed
            val cycle = (fallen / PETAL_FALL_RANGE).toInt()
            val yRatio = PETAL_START_Y + (fallen % PETAL_FALL_RANGE)
            val xRatio = 0.25f + 0.5f * hash01(index, cycle)
            // Fade in at the canopy and out near the ground so restarts are invisible.
            val travel = (yRatio - PETAL_START_Y) / PETAL_FALL_RANGE
            val fade = (minOf(travel, 1f - travel) * 8f).coerceIn(0f, 1f)

            val driftX = petal.driftAmp * sin(yRatio * 2f * PI.toFloat() * petal.driftFreq + time)
            val petalX = (xRatio * width + driftX).coerceIn(0f, width)
            val petalY = yRatio * height

            val rot = petal.baseAngle + time * petal.rotationSpeed * 20f
            drawPetal(petalX, petalY, petal.size, rot, petal.color.copy(alpha = petal.color.alpha * fade))
        }
    }
}

private fun DrawScope.drawBranch(
    startX: Float,
    startY: Float,
    length: Float,
    angleRad: Float,
    depth: Int,
    maxDepth: Int,
    progress: Float,
    windTime: Float,
    trunkColor: Color,
    foliageColor: Color,
    leafColor: Color
) {
    if (depth > maxDepth || length < 4f) return

    val endX = startX + length * cos(angleRad)
    val endY = startY + length * sin(angleRad)

    val strokeWidth = ((maxDepth - depth + 1) * 2.2f).coerceAtLeast(1.5f)
    drawLine(
        color = trunkColor.copy(alpha = 0.85f),
        start = Offset(startX, startY),
        end = Offset(endX, endY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // At terminal branches, bloom blossom clusters proportional to progress
    if (depth >= maxDepth - 2) {
        val blossomSize = (depth * 2.5f + 4f) * (0.3f + 0.7f * progress)
        val bloomAlpha = (0.4f + 0.6f * progress).coerceIn(0.1f, 0.95f)

        // Alternate cherry blossom petals and delicate green leaves
        val color = if (depth % 2 == 0) foliageColor.copy(alpha = bloomAlpha) else leafColor.copy(alpha = bloomAlpha * 0.9f)
        drawCircle(
            color = color,
            radius = blossomSize,
            center = Offset(endX, endY)
        )
        // Secondary soft blossom puff
        drawCircle(
            color = Color.White.copy(alpha = bloomAlpha * 0.5f),
            radius = blossomSize * 0.5f,
            center = Offset(endX - 2f, endY - 2f)
        )
    }

    val sway = sin(windTime + depth * 0.5f) * 0.08f
    val branchSpread = 0.42f
    val lengthFactor = 0.74f

    // Left child branch
    drawBranch(
        startX = endX,
        startY = endY,
        length = length * lengthFactor,
        angleRad = angleRad - branchSpread + sway,
        depth = depth + 1,
        maxDepth = maxDepth,
        progress = progress,
        windTime = windTime,
        trunkColor = trunkColor,
        foliageColor = foliageColor,
        leafColor = leafColor
    )

    // Right child branch
    drawBranch(
        startX = endX,
        startY = endY,
        length = length * lengthFactor * 0.92f,
        angleRad = angleRad + branchSpread + sway,
        depth = depth + 1,
        maxDepth = maxDepth,
        progress = progress,
        windTime = windTime,
        trunkColor = trunkColor,
        foliageColor = foliageColor,
        leafColor = leafColor
    )
}

private fun DrawScope.drawPetal(x: Float, y: Float, size: Float, rotationDeg: Float, color: Color) {
    val rad = rotationDeg * (PI.toFloat() / 180f)
    val dx = (size * 0.8f) * cos(rad)
    val dy = (size * 0.8f) * sin(rad)

    val path = Path().apply {
        moveTo(x - dx, y - dy)
        quadraticTo(x - dy * 0.5f, y + dx * 0.5f, x + dx, y + dy)
        quadraticTo(x + dy * 0.5f, y - dx * 0.5f, x - dx, y - dy)
        close()
    }
    drawPath(path, color = color)
}
