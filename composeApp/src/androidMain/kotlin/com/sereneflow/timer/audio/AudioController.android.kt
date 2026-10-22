package com.sereneflow.timer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import com.sereneflow.timer.platform.AndroidAppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class AndroidAudioController : AudioController {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        initTts()
    }

    private fun initTts() {
        val context = AndroidAppContext.appContext ?: return
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    tts?.setPitch(0.95f)
                    tts?.setSpeechRate(0.88f)
                    isTtsReady = true
                }
            }
        } catch (_: Exception) {}
    }

    override fun playTibetanBowl(fundamentalHz: Double, durationMs: Long) {
        scope.launch { play(ToneSynth.bowl(fundamentalHz, durationMs), durationMs) }
    }

    override fun playZenChime(frequencyHz: Double, durationMs: Long) {
        scope.launch { play(ToneSynth.chime(frequencyHz, durationMs), durationMs) }
    }

    override fun playStepComplete() {
        playZenChime(660.0, 1500)
    }

    override fun playRoutineComplete() {
        playTibetanBowl(432.0, 3500)
    }

    override fun speak(text: String) {
        if (tts == null && AndroidAppContext.appContext != null) {
            initTts()
        }
        if (isTtsReady && tts != null) {
            try {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "step_guidance_${System.currentTimeMillis()}")
            } catch (_: Exception) {}
        }
    }

    override fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    private suspend fun play(samples: FloatArray, durationMs: Long) {
        val buffer = ShortArray(samples.size) { (samples[it] * 32767).toInt().toShort() }
        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(ToneSynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (_: Exception) {
            return
        }

        try {
            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            delay(durationMs + 200)
        } catch (_: Exception) {
        } finally {
            audioTrack.release()
        }
    }
}

actual fun createAudioController(): AudioController = AndroidAudioController()
