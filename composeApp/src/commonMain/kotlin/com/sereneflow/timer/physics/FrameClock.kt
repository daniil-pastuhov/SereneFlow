package com.sereneflow.timer.physics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlin.math.floor

/**
 * Seconds since first composition, updated on every display frame.
 *
 * Canvases should read this inside their draw lambda (not during composition) so each frame only
 * re-draws, and should derive all motion from it as pure functions of time. That keeps animation
 * speed independent of the frame rate (60 vs 120 Hz) and avoids the jumps that looping
 * `infiniteRepeatable` transitions cause when their phase wraps around.
 */
@Composable
fun rememberFrameTimeSeconds(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos -> time.floatValue = (frameNanos - start) / 1_000_000_000f }
        }
    }
    return time
}

/** Fractional part in [0, 1). */
internal fun fract(x: Float): Float = x - floor(x)

/** Deterministic pseudo-random value in [0, 1) for particle [index] and [salt]. */
internal fun hash01(index: Int, salt: Int = 0): Float {
    var h = index * 374761393 + salt * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0x7FFFFFFF) / 2147483648f
}
