package com.sereneflow.timer.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Additive-synthesis bell tones shared by the platform audio controllers. */
object ToneSynth {
    const val SAMPLE_RATE = 44100

    /** Tibetan singing bowl: inharmonic partials of a struck bowl with a slow shimmer. */
    fun bowl(fundamentalHz: Double, durationMs: Long): FloatArray = synthesize(
        durationMs = durationMs,
        components = listOf(
            fundamentalHz to 1.0,
            fundamentalHz * 2.76 to 0.45,
            fundamentalHz * 5.40 to 0.20
        ),
        decayTauSeconds = durationMs / 1000.0 * 0.45,
        shimmerRateHz = 2.5
    )

    /** Soft chime: harmonic partials with a quicker decay. */
    fun chime(frequencyHz: Double, durationMs: Long): FloatArray = synthesize(
        durationMs = durationMs,
        components = listOf(
            frequencyHz to 1.0,
            frequencyHz * 2.0 to 0.35,
            frequencyHz * 3.0 to 0.15
        ),
        decayTauSeconds = durationMs / 1000.0 * 0.35,
        shimmerRateHz = 4.0
    )

    /** Mono samples in [-1, 1] at [SAMPLE_RATE]. */
    fun synthesize(
        durationMs: Long,
        components: List<Pair<Double, Double>>,
        decayTauSeconds: Double,
        shimmerRateHz: Double
    ): FloatArray {
        val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
        val totalWeight = components.sumOf { it.second }.coerceAtLeast(1.0)
        // Short attack ramp avoids an audible click at the start of the tone.
        val attackSamples = (SAMPLE_RATE * 0.005).toInt()

        return FloatArray(numSamples) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-t / decayTauSeconds)
            val shimmer = 1.0 + 0.12 * cos(2.0 * PI * shimmerRateHz * t)
            val attack = min(1.0, i.toDouble() / attackSamples)

            var sampleSum = 0.0
            for ((freq, weight) in components) {
                sampleSum += weight * sin(2.0 * PI * freq * t)
            }

            ((sampleSum / totalWeight) * decay * shimmer * attack * 0.8).coerceIn(-1.0, 1.0).toFloat()
        }
    }
}
