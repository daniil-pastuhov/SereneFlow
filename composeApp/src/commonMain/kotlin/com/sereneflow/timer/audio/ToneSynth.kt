package com.sereneflow.timer.audio

import com.sereneflow.timer.model.CueSound
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

    /** Crystal (quartz) bowl: an almost pure tone that rings for a long time. */
    fun crystalBowl(frequencyHz: Double = 528.0, durationMs: Long = 4000): FloatArray = synthesize(
        durationMs = durationMs,
        components = listOf(
            frequencyHz to 1.0,
            frequencyHz * 1.004 to 0.6, // a slightly detuned twin gives the slow beating of a bowl
            frequencyHz * 2.0 to 0.08
        ),
        decayTauSeconds = durationMs / 1000.0 * 0.5,
        shimmerRateHz = 1.2
    )

    /** Temple gong: low, dense inharmonic partials with a slow swell of shimmer. */
    fun templeGong(fundamentalHz: Double = 110.0, durationMs: Long = 4500): FloatArray = synthesize(
        durationMs = durationMs,
        components = listOf(
            fundamentalHz to 1.0,
            fundamentalHz * 1.47 to 0.6,
            fundamentalHz * 2.09 to 0.45,
            fundamentalHz * 2.56 to 0.3,
            fundamentalHz * 3.30 to 0.18
        ),
        decayTauSeconds = durationMs / 1000.0 * 0.4,
        shimmerRateHz = 0.8
    )

    /** Kalimba: a plucked metal tine, bright at first and quick to fade. */
    fun kalimba(frequencyHz: Double = 523.25, durationMs: Long = 1600): FloatArray = synthesize(
        durationMs = durationMs,
        components = listOf(
            frequencyHz to 1.0,
            frequencyHz * 2.0 to 0.12,
            frequencyHz * 5.9 to 0.1
        ),
        decayTauSeconds = durationMs / 1000.0 * 0.22,
        shimmerRateHz = 6.0
    )

    /** Wood block: a short, hollow knock. */
    fun woodBlock(frequencyHz: Double = 820.0, durationMs: Long = 400): FloatArray = synthesize(
        durationMs = durationMs,
        components = listOf(
            frequencyHz to 1.0,
            frequencyHz * 1.58 to 0.5,
            frequencyHz * 2.44 to 0.25
        ),
        decayTauSeconds = 0.045,
        shimmerRateHz = 0.0
    )

    /** Koshi chimes: a few wind-chime notes falling one after another. */
    fun koshiChimes(durationMs: Long = 2600): FloatArray {
        val notes = listOf(1318.5, 1174.7, 987.8, 1174.7, 784.0)
        val noteMs = 1600L
        val gapMs = (durationMs - noteMs) / (notes.size - 1)
        val mix = FloatArray((SAMPLE_RATE * (durationMs / 1000.0)).toInt())
        notes.forEachIndexed { i, frequency ->
            val offset = (SAMPLE_RATE * (i * gapMs / 1000.0)).toInt()
            chime(frequency, noteMs).forEachIndexed { j, sample ->
                if (offset + j < mix.size) mix[offset + j] += sample * 0.45f
            }
        }
        return FloatArray(mix.size) { mix[it].coerceIn(-1f, 1f) }
    }

    /** The samples of a step-start [sound]; empty for [CueSound.NONE]. */
    fun render(sound: CueSound): FloatArray = when (sound) {
        CueSound.TIBETAN_BOWL -> bowl(432.0, 3000)
        CueSound.ZEN_CHIME -> chime(720.0, 1400)
        CueSound.CRYSTAL_BOWL -> crystalBowl()
        CueSound.TEMPLE_GONG -> templeGong()
        CueSound.KALIMBA -> kalimba()
        CueSound.WOOD_BLOCK -> woodBlock()
        CueSound.KOSHI_CHIMES -> koshiChimes()
        CueSound.NONE -> FloatArray(0)
    }

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
