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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val TAU = 2f * PI.toFloat()
private const val MAX_DEPTH = 6
private const val MAX_TIPS = 512
private const val FALLING_PETALS = 30
private const val MOTES = 12
private const val PETAL_START_Y = 0.2f
private const val PETAL_FALL_RANGE = 0.58f

/** Per-canvas buffers reused every frame, so drawing allocates nothing per branch. */
private class TreeScratch {
    val branchPath = Path()
    // A cherry-blossom petal, one unit long, base at the origin, pointing up, with a notch at its tip.
    val petalPath = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-0.55f, -0.25f, -0.55f, -0.85f, -0.18f, -1f)
        lineTo(0f, -0.84f)
        lineTo(0.18f, -1f)
        cubicTo(0.55f, -0.85f, 0.55f, -0.25f, 0f, 0f)
        close()
    }
    val tipX = FloatArray(MAX_TIPS)
    val tipY = FloatArray(MAX_TIPS)
    val tipDepth = IntArray(MAX_TIPS)
    val tipId = IntArray(MAX_TIPS)
    val tipGrow = FloatArray(MAX_TIPS)
    val tipOuter = FloatArray(MAX_TIPS)
    var tipCount = 0
}

/** What every branch of one frame needs to know. */
private class TreeFrame(
    val scratch: TreeScratch,
    val levels: Float,
    /** The first limbs' length; the trunk's own length only lifts the crown. */
    val crownLength: Float,
    /** How far children turn from their parent, at least; more makes a broader crown. */
    val spread: Float,
    /** How much each branch leans back toward upright; less lets the crown spread sideways. */
    val lean: Float,
    val seconds: Float,
    val gust: Float,
    val barkColor: Color,
    val barkHighlight: Color
)

/**
 * A bonsai that grows through the step: it starts as a young tree and adds a level of branches
 * as time passes, its foliage pads move out to the new tips, and its blossoms open one by one
 * until it is in full bloom at the end. Wind sways it and petals drift down to the ground.
 */
@Composable
fun BonsaiTreeCanvas(
    progress: () -> Float, // 0f to 1f (growing and blooming)
    modifier: Modifier = Modifier,
    trunkColor: Color = Color(0xFF4A3B32),
    foliageColor: Color = Color(0xFFE57373),
    leafColor: Color = Color(0xFF81C784)
) {
    val frameTime = rememberFrameTimeSeconds()
    val scratch = remember { TreeScratch() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val progress = progress().coerceIn(0f, 1f)
        val seconds = frameTime.value
        // Wind: a slow swell every 6 seconds with a quicker gust over it.
        val gust = 0.7f * sin(seconds * TAU / 6f) + 0.3f * sin(seconds * TAU / 2.3f + 1f)
        val unit = min(width, height * 0.62f)
        // The tree stands just below the countdown and its crown rises above it.
        val groundY = height * 0.76f
        val rootX = width * 0.5f
        val rootY = groundY - unit * 0.01f
        // The trunk forks behind the countdown, and the crown is sized on its own to reach from
        // there to just under the top bar while staying within the screen's width. In a wide
        // window the countdown takes most of the height, so the tree forks lower and its crown
        // spreads out to both sides of the countdown instead of rising above it.
        val wide = width > height * 1.15f
        val forkY = height * (if (wide) 0.6f else 0.47f)
        val trunkLength = rootY - forkY
        val crownLength = if (wide) min(height * 0.24f, width * 0.14f) else min(height * 0.135f, width * 0.37f)
        val canopyY = forkY - crownLength * 1.2f

        // Soft light behind the canopy, warming as the tree blooms.
        val glowRadius = unit * (0.62f + 0.03f * sin(seconds * TAU / 7f))
        val glowCenter = Offset(rootX, canopyY)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(foliageColor.copy(alpha = 0.08f + 0.1f * progress), Color.Transparent),
                center = glowCenter,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = glowCenter
        )

        drawGround(scratch, width, height, groundY, unit, rootX, progress, seconds, gust, trunkColor, foliageColor, leafColor)

        // The tree starts a little over three levels tall and has all seven by the end.
        val frame = TreeFrame(
            scratch = scratch,
            levels = (0.45f + 0.55f * progress) * (MAX_DEPTH + 1),
            crownLength = crownLength,
            spread = if (wide) 0.5f else 0.36f,
            lean = if (wide) 0.04f else 0.18f,
            seconds = seconds,
            gust = gust,
            barkColor = trunkColor.copy(alpha = 0.8f),
            barkHighlight = lerp(trunkColor, Color.White, 0.35f).copy(alpha = 0.35f)
        )
        scratch.tipCount = 0
        drawBranch(
            frame = frame,
            startX = rootX,
            startY = rootY,
            length = trunkLength,
            angleRad = -PI.toFloat() / 2f,
            width = unit * 0.05f,
            depth = 0,
            id = 1
        )

        drawFoliage(scratch, unit, seconds, progress, leafColor, foliageColor)
        drawFallingPetals(scratch, width, height, unit, seconds, gust, progress, foliageColor)
        drawMotes(width, height, unit, seconds, rootX, groundY, canopyY, foliageColor)
    }
}

/** How much of the branch at [depth] has grown: 0 not yet, 1 full length, eased in between. */
private fun TreeFrame.grow(depth: Int): Float {
    val t = (levels - depth).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Draws one tapered, slightly curved branch and its children, and records the tips that carry
 * foliage for [drawFoliage]. [id] is unique per branch (parent * 3 + child), for its jitter.
 */
private fun DrawScope.drawBranch(
    frame: TreeFrame,
    startX: Float,
    startY: Float,
    length: Float,
    angleRad: Float,
    width: Float,
    depth: Int,
    id: Int
) {
    val grow = frame.grow(depth)
    if (grow <= 0f) return

    // Wind bends the outer branches most; each also flutters a little on its own.
    val sway = frame.gust * 0.03f * (0.3f + 0.6f * depth) +
        0.012f * depth * sin(frame.seconds * 2.6f + hash01(id, 1) * TAU)
    val angle = angleRad + sway
    val len = length * grow
    val endX = startX + len * cos(angle)
    val endY = startY + len * sin(angle)
    val endWidth = width * 0.68f
    val startWidth = width * (0.6f + 0.4f * grow)
    val tipWidth = endWidth * grow

    // Tapered body: two quadratic edges bowed to one side, so branches curve like wood.
    val nx = -sin(angle)
    val ny = cos(angle)
    val bend = len * (if (depth == 0) 0.16f else 0.1f) * (hash01(id, 2) * 2f - 1f)
    val midX = (startX + endX) * 0.5f + nx * bend
    val midY = (startY + endY) * 0.5f + ny * bend
    val halfStart = startWidth * 0.5f
    val halfEnd = tipWidth * 0.5f
    val halfMid = (halfStart + halfEnd) * 0.5f
    val path = frame.scratch.branchPath
    path.reset()
    path.moveTo(startX + nx * halfStart, startY + ny * halfStart)
    path.quadraticTo(midX + nx * halfMid, midY + ny * halfMid, endX + nx * halfEnd, endY + ny * halfEnd)
    path.lineTo(endX - nx * halfEnd, endY - ny * halfEnd)
    path.quadraticTo(midX - nx * halfMid, midY - ny * halfMid, startX - nx * halfStart, startY - ny * halfStart)
    path.close()
    drawPath(path, color = frame.barkColor)
    drawCircle(color = frame.barkColor, radius = halfEnd, center = Offset(endX, endY))
    // Light catching the bark of the thicker limbs.
    if (depth <= 2 && halfMid > 1.5f) {
        drawLine(
            color = frame.barkHighlight,
            start = Offset(startX - nx * halfStart * 0.45f, startY - ny * halfStart * 0.45f),
            end = Offset(midX - nx * halfMid * 0.45f, midY - ny * halfMid * 0.45f),
            strokeWidth = halfMid * 0.35f,
            cap = StrokeCap.Round
        )
    }

    val scratch = frame.scratch
    if (depth >= 3 && scratch.tipCount < MAX_TIPS) {
        val i = scratch.tipCount++
        scratch.tipX[i] = endX
        scratch.tipY[i] = endY
        scratch.tipDepth[i] = depth
        scratch.tipId[i] = id
        scratch.tipGrow[i] = grow
        // An inner tip's pad thins out as the branches beyond it grow.
        scratch.tipOuter[i] = if (depth == MAX_DEPTH) 1f else 1f - 0.5f * frame.grow(depth + 1)
    }
    if (depth == MAX_DEPTH) return

    // Two children, and on a few of the lower limbs a third, middle one, for a fuller crown.
    val children = if (depth in 1..2 && hash01(id, 3) > 0.55f) 3 else 2
    for (k in 0 until children) {
        val childId = id * 3 + k
        val side = when {
            children == 3 && k == 2 -> 0f
            k == 0 -> -1f
            else -> 1f
        }
        val spread = frame.spread + 0.22f * hash01(childId, 4)
        // Lean every branch a little back toward upright, so the crown stays within the screen.
        val raw = angle + side * spread
        val childAngle = raw + (-PI.toFloat() / 2f - raw) * frame.lean
        drawBranch(
            frame = frame,
            startX = endX,
            startY = endY,
            length = (if (depth == 0) frame.crownLength else length) * (0.66f + 0.16f * hash01(childId, 5)) * (if (side == 0f) 0.8f else 1f),
            angleRad = childAngle,
            width = endWidth,
            depth = depth + 1,
            id = childId
        )
    }
}

/**
 * Foliage pads on the outer tips and the blossoms on them. A pad on an inner tip thins out as
 * the branches beyond it grow, so the foliage follows the tree outward.
 */
private fun DrawScope.drawFoliage(
    scratch: TreeScratch,
    unit: Float,
    seconds: Float,
    progress: Float,
    leafColor: Color,
    blossomColor: Color
) {
    val shade = lerp(leafColor, Color.Black, 0.25f)
    val light = lerp(leafColor, Color.White, 0.35f)
    // Shadows first, then leaves, then highlights, so neighbouring pads merge into one canopy.
    for (layer in 0..2) {
        for (i in 0 until scratch.tipCount) {
            val depth = scratch.tipDepth[i]
            val id = scratch.tipId[i]
            val r = unit * (0.026f + 0.006f * (MAX_DEPTH - depth)) * scratch.tipGrow[i] * scratch.tipOuter[i]
            if (r < 0.5f) continue
            val rustle = r * 0.08f * sin(seconds * 3.1f + hash01(id, 6) * TAU)
            val x = scratch.tipX[i] + rustle
            val y = scratch.tipY[i]
            for (p in 0..2) {
                val a = hash01(id, 10 + p) * TAU
                val pr = r * (0.6f + 0.4f * hash01(id, 20 + p))
                val px = x + cos(a) * r * 0.55f
                val py = y + sin(a) * r * 0.35f
                when (layer) {
                    0 -> drawCircle(shade.copy(alpha = 0.85f), pr, Offset(px, py + pr * 0.25f))
                    1 -> drawCircle(leafColor, pr * 0.88f, Offset(px - pr * 0.08f, py - pr * 0.08f))
                    else -> drawCircle(light.copy(alpha = 0.55f), pr * 0.42f, Offset(px - pr * 0.3f, py - pr * 0.35f))
                }
            }
        }
    }

    // Blossoms on the two outermost levels, each opening at its own moment in the step.
    val petal = lerp(blossomColor, Color.White, 0.2f)
    val heart = lerp(blossomColor, Color(0xFFFFE082), 0.6f)
    for (i in 0 until scratch.tipCount) {
        val depth = scratch.tipDepth[i]
        if (depth < MAX_DEPTH - 1) continue
        val id = scratch.tipId[i]
        val opensAt = 0.1f + 0.8f * hash01(id, 30)
        val t = ((progress - opensAt) / 0.12f).coerceIn(0f, 1f) * scratch.tipGrow[i]
        if (t <= 0f) continue
        val open = t * t * (3f - 2f * t)
        val r = unit * 0.022f * open
        val a = hash01(id, 31) * TAU
        val cx = scratch.tipX[i] + cos(a) * unit * 0.012f
        val cy = scratch.tipY[i] + sin(a) * unit * 0.008f
        val turn = hash01(id, 32) * 72f + 10f * sin(seconds * 1.7f + id)
        for (k in 0 until 5) {
            translate(cx, cy) {
                rotate(degrees = turn + k * 72f, pivot = Offset.Zero) {
                    scale(scaleX = r, scaleY = r, pivot = Offset.Zero) {
                        drawPath(scratch.petalPath, color = petal)
                    }
                }
            }
        }
        drawCircle(heart, r * 0.22f, Offset(cx, cy))
    }
}

/** A mound of earth with moss at the tree's foot, collecting the petals that have fallen. */
private fun DrawScope.drawGround(
    scratch: TreeScratch,
    width: Float,
    height: Float,
    groundY: Float,
    unit: Float,
    rootX: Float,
    progress: Float,
    seconds: Float,
    gust: Float,
    earthColor: Color,
    petalColor: Color,
    mossColor: Color
) {
    val back = Path().apply {
        moveTo(0f, height)
        lineTo(0f, groundY - unit * 0.02f)
        quadraticTo(width * 0.3f, groundY - unit * 0.1f, width, groundY - unit * 0.03f)
        lineTo(width, height)
        close()
    }
    drawPath(back, color = earthColor.copy(alpha = 0.08f))
    val front = Path().apply {
        moveTo(0f, height)
        lineTo(0f, groundY + unit * 0.04f)
        quadraticTo(width * 0.5f, groundY - unit * 0.05f, width, groundY + unit * 0.04f)
        lineTo(width, height)
        close()
    }
    drawPath(
        front,
        brush = Brush.verticalGradient(
            colors = listOf(earthColor.copy(alpha = 0.2f), earthColor.copy(alpha = 0.06f)),
            startY = groundY - unit * 0.05f,
            endY = height
        )
    )
    // Moss around the roots.
    for (i in 0 until 7) {
        val x = rootX + (hash01(i, 40) - 0.5f) * unit * 0.32f
        val r = unit * (0.02f + 0.02f * hash01(i, 41))
        drawCircle(mossColor.copy(alpha = 0.45f), r, Offset(x, groundY + unit * 0.005f))
    }
    // Grass blades bending with the wind.
    for (i in 0 until 16) {
        val x = width * (0.05f + 0.9f * hash01(i, 42))
        val y = groundY + unit * 0.02f
        val h = unit * (0.025f + 0.025f * hash01(i, 43))
        val lean = (gust * 0.35f + 0.15f * sin(seconds * 2f + i)) * h
        drawLine(
            color = mossColor.copy(alpha = 0.5f),
            start = Offset(x, y),
            end = Offset(x + lean, y - h),
            strokeWidth = unit * 0.004f,
            cap = StrokeCap.Round
        )
    }
    // Fallen petals gather as the step goes on.
    val fallen = (progress * 26f).toInt()
    for (i in 0 until fallen) {
        val x = rootX + (hash01(i, 44) - 0.5f) * width * 0.8f
        val y = groundY + unit * (0.015f + 0.05f * hash01(i, 45))
        val s = unit * (0.008f + 0.006f * hash01(i, 46))
        translate(x, y) {
            rotate(degrees = hash01(i, 47) * 360f, pivot = Offset.Zero) {
                scale(scaleX = s * 0.6f, scaleY = s, pivot = Offset.Zero) {
                    drawPath(scratch.petalPath, color = petalColor.copy(alpha = 0.6f))
                }
            }
        }
    }
}

/** Petals drifting from the canopy to the ground, tumbling as they fall; more as the tree blooms. */
private fun DrawScope.drawFallingPetals(
    scratch: TreeScratch,
    width: Float,
    height: Float,
    unit: Float,
    seconds: Float,
    gust: Float,
    progress: Float,
    color: Color
) {
    for (i in 0 until FALLING_PETALS) {
        if (hash01(i, 50) > 0.25f + 0.75f * progress) continue
        val speed = 0.035f + 0.035f * hash01(i, 51) // screen heights per second
        val fallen = hash01(i, 52) * PETAL_FALL_RANGE + seconds * speed
        val cycle = (fallen / PETAL_FALL_RANGE).toInt()
        val yRatio = PETAL_START_Y + (fallen % PETAL_FALL_RANGE)
        // Fade in at the canopy and out near the ground so restarts are invisible.
        val travel = (yRatio - PETAL_START_Y) / PETAL_FALL_RANGE
        val fade = (min(travel, 1f - travel) * 8f).coerceIn(0f, 1f)
        val xRatio = 0.2f + 0.6f * hash01(i, cycle + 53)
        val drift = unit * (0.03f * sin(yRatio * TAU * (1f + hash01(i, 54)) + seconds) + 0.06f * gust * travel)
        val x = (xRatio * width + drift).coerceIn(0f, width)
        val y = yRatio * height
        val s = unit * (0.013f + 0.008f * hash01(i, 55))
        // Tumbling: the petal turns and flips, so it narrows to an edge and widens again.
        val flip = cos(seconds * (1.5f + 1.5f * hash01(i, 56)) + hash01(i, 57) * TAU)
        val spin = hash01(i, 58) * 360f + seconds * (40f + 60f * hash01(i, 59))
        translate(x, y) {
            rotate(degrees = spin, pivot = Offset.Zero) {
                scale(scaleX = s * (0.25f + 0.75f * abs(flip)), scaleY = s, pivot = Offset.Zero) {
                    translate(0f, 0.5f) {
                        drawPath(scratch.petalPath, color = color.copy(alpha = 0.85f * fade))
                    }
                }
            }
        }
    }
}

/** Glowing motes of pollen rising slowly from the ground into the canopy. */
private fun DrawScope.drawMotes(
    width: Float,
    height: Float,
    unit: Float,
    seconds: Float,
    rootX: Float,
    groundY: Float,
    canopyY: Float,
    color: Color
) {
    val glow = lerp(color, Color.White, 0.5f)
    for (i in 0 until MOTES) {
        val t = fract(hash01(i, 60) + seconds / (9f + 6f * hash01(i, 61)))
        val y = groundY + (canopyY - unit * 0.2f - groundY) * t
        val x = rootX + (hash01(i, 62) - 0.5f) * width * 0.85f + unit * 0.03f * sin(seconds * 0.8f + i * 1.7f)
        val fade = sin(t * PI.toFloat()) * (0.6f + 0.4f * sin(seconds * 3f + i))
        val r = unit * (0.004f + 0.003f * hash01(i, 63))
        drawCircle(glow.copy(alpha = 0.18f * fade), r * 3f, Offset(x, y.coerceIn(0f, height)))
        drawCircle(glow.copy(alpha = 0.7f * fade), r, Offset(x, y.coerceIn(0f, height)))
    }
}
