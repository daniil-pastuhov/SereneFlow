package com.sereneflow.timer.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.platform.AndroidAppContext
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AndroidAudioController : AudioController {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val ttsReady = CompletableDeferred<Boolean>()
    // Set from the UI thread, read where speech is started.
    @Volatile private var voiceId: String? = null

    init {
        initTts()
    }

    private fun initTts() {
        val context = AndroidAppContext.appContext ?: return
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    // The prompts are English, whatever the device language.
                    tts?.language = Locale.US
                    tts?.setPitch(0.95f)
                    tts?.setSpeechRate(0.88f)
                    isTtsReady = true
                }
                ttsReady.complete(status == TextToSpeech.SUCCESS)
            }
        } catch (_: Exception) {}
    }

    override fun playTibetanBowl(fundamentalHz: Double, durationMs: Long) {
        scope.launch { play(ToneSynth.bowl(fundamentalHz, durationMs), durationMs) }
    }

    override fun playZenChime(frequencyHz: Double, durationMs: Long) {
        scope.launch { play(ToneSynth.chime(frequencyHz, durationMs), durationMs) }
    }

    override fun playSound(sound: CueSound) {
        val samples = ToneSynth.render(sound)
        if (samples.isEmpty()) return
        scope.launch { play(samples, samples.size * 1000L / ToneSynth.SAMPLE_RATE) }
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
                // Without an on-device English voice it stays silent rather than use the network.
                if (!applyVoice()) return
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "step_guidance_${System.currentTimeMillis()}")
            } catch (_: Exception) {}
        }
    }

    override suspend fun englishVoices(): List<VoiceOption> {
        if (withTimeoutOrNull(5_000) { ttsReady.await() } != true) return emptyList()
        val voices = try {
            tts?.voices.orEmpty()
        } catch (_: Exception) {
            return emptyList()
        }
        return voices
            // Only voices that run on the device: the privacy policy promises speech stays there.
            .filter { it.locale.language == "en" && it.runsOnDevice() }
            .sortedWith(compareBy<Voice>({ it.locale.toLanguageTag() }, { -it.quality }, { it.name }))
            .groupBy { it.locale }
            .flatMap { (locale, sameLocale) ->
                // Engine voice names ("en-us-x-iol-local") mean nothing to people: number them.
                sameLocale.mapIndexed { index, voice ->
                    VoiceOption(
                        id = voice.name,
                        name = "Voice ${index + 1}",
                        detail = locale.getDisplayName(Locale.US) +
                            if (voice.quality >= Voice.QUALITY_HIGH) " · High quality" else ""
                    )
                }
            }
    }

    override fun setVoice(id: String?) {
        voiceId = id
    }

    /** The voice [applyVoice] last set up; a sentinel until the first speech. */
    @Volatile private var appliedVoiceId: String? = NOT_APPLIED

    /**
     * Sets up the chosen voice, or for the default one the engine's US English voice, swapped for
     * an on-device English one when the engine's choice needs the network: the privacy policy
     * promises speech stays on the device. Only re-applied when the choice changes.
     *
     * @return whether the engine now speaks on the device; if not, nothing should be spoken.
     */
    private fun applyVoice(): Boolean {
        val engine = tts ?: return false
        if (appliedVoiceId != voiceId) {
            val onDevice = engine.voices.orEmpty().filter { it.runsOnDevice() }
            val chosen = voiceId?.let { id -> onDevice.firstOrNull { it.name == id } }
            if (chosen != null) {
                engine.voice = chosen
            } else {
                // Resets the voice to the engine's default for the language.
                engine.language = Locale.US
                if (engine.voice?.runsOnDevice() != true) {
                    val english = onDevice.filter { it.locale.language == "en" }
                    (english.filter { it.locale == Locale.US }.ifEmpty { english })
                        .maxByOrNull { it.quality }?.let { engine.voice = it }
                }
            }
            appliedVoiceId = voiceId
        }
        return engine.voice?.runsOnDevice() == true
    }

    override fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    private val vibrator: Vibrator? by lazy {
        val context = AndroidAppContext.appContext ?: return@lazy null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun haptic(kind: HapticKind) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> vibrator.vibrate(
                    when (kind) {
                        HapticKind.STEP_START -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                        HapticKind.COUNTDOWN_TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                        HapticKind.ROUTINE_COMPLETE -> VibrationEffect.createWaveform(COMPLETE_PATTERN, -1)
                    }
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> vibrator.vibrate(
                    when (kind) {
                        HapticKind.STEP_START -> VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
                        HapticKind.COUNTDOWN_TICK -> VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                        HapticKind.ROUTINE_COMPLETE -> VibrationEffect.createWaveform(COMPLETE_PATTERN, -1)
                    }
                )
                else -> @Suppress("DEPRECATION") when (kind) {
                    HapticKind.STEP_START -> vibrator.vibrate(60)
                    HapticKind.COUNTDOWN_TICK -> vibrator.vibrate(20)
                    HapticKind.ROUTINE_COMPLETE -> vibrator.vibrate(COMPLETE_PATTERN, -1)
                }
            }
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

private const val NOT_APPLIED = "\u0000not applied"

/** Off/on durations (ms) of the completion buzz: three gentle pulses. */
private val COMPLETE_PATTERN = longArrayOf(0, 80, 120, 80, 120, 160)

/**
 * Synthesised on the device, without the engine's servers. The app has no INTERNET permission, but
 * the speech engine runs in its own process and could use the network for these.
 */
private fun Voice.runsOnDevice(): Boolean =
    !isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features
