package com.sereneflow.timer.audio

import com.sereneflow.timer.model.CueSound
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesisVoiceQualityEnhanced
import platform.AVFAudio.AVSpeechSynthesisVoiceQualityPremium
import platform.AVFAudio.AVSpeechSynthesisVoiceTraitIsNoveltyVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.AVFAudio.setActive
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleIdentifier
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_time
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Plays [ToneSynth] tones through AVAudioEngine and speaks with AVSpeechSynthesizer.
 * The `.playback` session category keeps sounds audible with the ringer switch on silent.
 * All AVFoundation work is hopped to the main queue, wherever the caller is.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSAudioController : AudioController {
    // Replaced, with the engine and players, when the media services are reset (see rebuild).
    private var speechSynthesizer = AVSpeechSynthesizer()
    private var engine = AVAudioEngine()
    private val format = AVAudioFormat(standardFormatWithSampleRate = ToneSynth.SAMPLE_RATE.toDouble(), channels = 1u)

    // A small pool so overlapping tones (countdown chime over a bowl) don't queue behind each other.
    private var players = makePlayers()
    private var nextPlayer = 0
    private var isSetUp = false
    private var playersAttached = false
    private var sessionActive = false
    private var voiceId: String? = null
    private var idleGeneration = 0

    private fun ensureEngine(): Boolean {
        if (!isSetUp) {
            configureSession()
            observeSystemChanges()
            isSetUp = true
        }
        if (!playersAttached) {
            players.forEach { player ->
                engine.attachNode(player)
                engine.connect(player, to = engine.mainMixerNode, format = format)
            }
            playersAttached = true
        }
        if (!engine.running) {
            engine.prepare()
            if (!engine.startAndReturnError(null)) return false
        }
        return true
    }

    private fun configureSession() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, withOptions = AVAudioSessionCategoryOptionMixWithOthers, error = null)
        session.setActive(true, error = null)
    }

    /**
     * The system stops the engine on an interruption (a phone call, Siri) and on a configuration
     * change (headphones or a Bluetooth speaker connected or removed, a sample-rate change). While a
     * routine runs, the running engine is what keeps the app alive in the background, so restart it.
     */
    private fun observeSystemChanges() {
        val center = NSNotificationCenter.defaultCenter
        val session = AVAudioSession.sharedInstance()
        center.addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = session,
            queue = NSOperationQueue.mainQueue
        ) { notification ->
            val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
            if (type == AVAudioSessionInterruptionTypeEnded && sessionActive) {
                session.setActive(true, error = null)
                ensureEngine()
            }
        }
        // Not filtered by object: the engine is replaced after a media services reset.
        center.addObserverForName(
            name = AVAudioEngineConfigurationChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { notification ->
            if (notification?.`object` === engine && sessionActive) ensureEngine()
        }
        // The audio daemon restarted: every engine, node and synthesizer made before is now invalid.
        center.addObserverForName(
            name = AVAudioSessionMediaServicesWereResetNotification,
            `object` = session,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            rebuild()
            configureSession()
            if (sessionActive) ensureEngine()
        }
    }

    private fun rebuild() {
        engine.stop()
        engine = AVAudioEngine()
        players = makePlayers()
        nextPlayer = 0
        playersAttached = false
        speechSynthesizer = AVSpeechSynthesizer()
    }

    private fun makePlayers() = List(4) { AVAudioPlayerNode() }

    private fun playSamples(samples: FloatArray) = onMain {
        if (samples.isEmpty() || !ensureEngine()) return@onMain
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = samples.size.toUInt()) ?: return@onMain
        buffer.frameLength = samples.size.toUInt()
        val channel = buffer.floatChannelData?.get(0) ?: return@onMain
        for (i in samples.indices) channel[i] = samples[i]

        val player = players[nextPlayer]
        nextPlayer = (nextPlayer + 1) % players.size
        player.scheduleBuffer(buffer, completionHandler = null)
        if (!player.playing) player.play()
        if (!sessionActive) scheduleIdlePause()
    }

    override fun playTibetanBowl(fundamentalHz: Double, durationMs: Long) {
        playSamples(ToneSynth.bowl(fundamentalHz, durationMs))
    }

    override fun playZenChime(frequencyHz: Double, durationMs: Long) {
        playSamples(ToneSynth.chime(frequencyHz, durationMs))
    }

    override fun playSound(sound: CueSound) {
        playSamples(ToneSynth.render(sound))
    }

    override fun playStepComplete() {
        playZenChime(660.0, 1500)
    }

    override fun playRoutineComplete() {
        playTibetanBowl(432.0, 3500)
    }

    override fun speak(text: String) = onMain {
        ensureEngine()
        val utterance = AVSpeechUtterance(string = text)
        utterance.rate = 0.48f // Calm, measured speaking tempo
        utterance.pitchMultiplier = 0.95f
        // Prompts are English, so keep an English voice regardless of the device language.
        utterance.voice = voiceId?.let { AVSpeechSynthesisVoice.voiceWithIdentifier(it) }
            ?: AVSpeechSynthesisVoice.voiceWithLanguage("en-US")
        if (speechSynthesizer.speaking) {
            speechSynthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
        speechSynthesizer.speakUtterance(utterance)
        if (!sessionActive) scheduleIdlePause()
    }

    override suspend fun englishVoices(): List<VoiceOption> {
        val english = NSLocale(localeIdentifier = "en_US")
        // voiceTraits (novelty voices such as "Bubbles") only exists from iOS 17.
        val hasTraits = AVSpeechSynthesisVoice.instancesRespondToSelector(NSSelectorFromString("voiceTraits"))
        return AVSpeechSynthesisVoice.speechVoices()
            .filterIsInstance<AVSpeechSynthesisVoice>()
            .filter { it.language.startsWith("en") }
            .filterNot { hasTraits && (it.voiceTraits and AVSpeechSynthesisVoiceTraitIsNoveltyVoice) != 0uL }
            .sortedWith(compareBy<AVSpeechSynthesisVoice>({ -it.quality }, { it.language }, { it.name }))
            .map { voice ->
                val quality = when (voice.quality) {
                    AVSpeechSynthesisVoiceQualityPremium -> " · Premium"
                    AVSpeechSynthesisVoiceQualityEnhanced -> " · Enhanced"
                    else -> ""
                }
                VoiceOption(
                    id = voice.identifier,
                    name = voice.name,
                    detail = (english.displayNameForKey(NSLocaleIdentifier, voice.language) ?: voice.language) + quality
                )
            }
    }

    override fun setVoice(id: String?) {
        voiceId = id
    }

    override fun stop() = onMain {
        speechSynthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    // Feedback generators only work while the app is in the foreground, which is when they matter.
    override fun haptic(kind: HapticKind) = onMain {
        when (kind) {
            HapticKind.STEP_START ->
                UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).impactOccurred()
            HapticKind.COUNTDOWN_TICK ->
                UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
            HapticKind.ROUTINE_COMPLETE ->
                UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        }
    }

    /**
     * While a routine ticks, keep the engine (and so the app's audio output) running even between
     * cues. With the `audio` background mode this keeps the app alive in the background, so the
     * timer, cues and Live Activity updates continue. When the session goes idle, the engine is
     * paused after a grace period so a final bowl/voice cue can finish playing.
     */
    override fun setSessionActive(active: Boolean) = onMain {
        if (active == sessionActive) return@onMain
        sessionActive = active
        idleGeneration++
        if (active) {
            ensureEngine()
        } else {
            scheduleIdlePause()
        }
    }

    /**
     * Pauses the engine once nothing has played for the grace period. A running engine keeps the
     * app alive in the background, so any sound outside a routine (a Settings preview, cues of a
     * routine run without background mode) must end with this too. Must run on the main thread.
     */
    private fun scheduleIdlePause() {
        val generation = ++idleGeneration
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, IDLE_GRACE_NANOS),
            dispatch_get_main_queue()
        ) {
            if (generation != idleGeneration || sessionActive) return@dispatch_after
            // Still speaking: look again later rather than cut the voice off or never pause.
            if (speechSynthesizer.speaking) scheduleIdlePause() else engine.pause()
        }
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) { block() }
    }
}

private const val IDLE_GRACE_NANOS = 6_000_000_000L

actual fun createAudioController(): AudioController = IOSAudioController()
