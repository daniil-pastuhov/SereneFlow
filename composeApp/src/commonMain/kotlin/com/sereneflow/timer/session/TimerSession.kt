package com.sereneflow.timer.session

import androidx.compose.runtime.Composable
import com.sereneflow.timer.ExternalAction
import com.sereneflow.timer.TimerCommand
import com.sereneflow.timer.audio.AudioController
import com.sereneflow.timer.audio.createAudioController
import com.sereneflow.timer.engine.TimerEngine
import com.sereneflow.timer.model.AppPreferences
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineCues
import com.sereneflow.timer.model.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** [cues] are the routine's look and sound with its Default choices filled in at start. */
class ActiveSession(val routine: Routine, val cues: RoutineCues, val engine: TimerEngine)

/**
 * The single running routine, owned by the process rather than by a screen.
 *
 * Keeping it outside the composition lets the timer survive the UI going away (Android activity
 * recreation, app in background) and lets notification / Live Activity controls reach it.
 * State changes are mirrored to the platform [SessionPresenter].
 */
object TimerSession {
    // Main, like the UI and notification actions that call stop(), so a presenter update can't run
    // concurrently with stop() and re-post the notification or restart the service after it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    val audioController: AudioController by lazy { createAudioController() }
    private val presenter: SessionPresenter by lazy { createSessionPresenter() }
    private var mirrorJob: Job? = null

    /**
     * @param backgroundMode mirror the session to the notification / Live Activity and keep it
     * alive with the screen off. When false the timer still keeps correct time (it is
     * wall-clock based) but nothing is shown or played outside the app, except on a companion
     * device (the Apple Watch app), which follows every session.
     * @param restTitle what the step between rounds is called ("Rest", from resources).
     */
    fun start(routine: Routine, preferences: AppPreferences, backgroundMode: Boolean, restTitle: String): ActiveSession {
        stop()
        val cues = routine.resolve(preferences)
        // Rounds become a flat list of steps, so the engine runs them like any routine.
        val engine = TimerEngine(
            routine.expandedForRounds(restTitle), cues, audioController, preferences.reminderThresholds,
            haptics = preferences.hapticsEnabled
        )
        val session = ActiveSession(routine, cues, engine)
        _active.value = session
        engine.start()

        mirrorJob = engine.timerState
            // Every meaningful change, plus a refresh every few seconds for progress bars.
            .distinctUntilChangedBy { state ->
                listOf(
                    state.currentStepIndex, state.isPaused, state.isRunning, state.isCompleted,
                    state.isMuted, state.stepExtensionMs, state.secondsRemainingInStep / PROGRESS_REFRESH_SECONDS
                )
            }
            .onEach { state ->
                // A late emission from a session that was stopped must not resurrect its notification.
                if (_active.value !== session) return@onEach
                val snapshot = state.toSnapshot(routine)
                presenter.showOnCompanion(snapshot)
                if (!backgroundMode) return@onEach
                audioController.setSessionActive(state.isRunning && !state.isPaused)
                presenter.show(snapshot)
            }
            .launchIn(scope)

        return session
    }

    fun stop() {
        val session = _active.value ?: return
        // Cleared first: a mirror emission already in flight then sees the session is gone.
        _active.value = null
        mirrorJob?.cancel()
        mirrorJob = null
        session.engine.stop()
        audioController.setSessionActive(false)
        presenter.end()
    }

    fun togglePlayPause() {
        _active.value?.engine?.togglePlayPause()
    }

    /**
     * Pauses the running routine; does nothing if it is already paused. For a remote control (the
     * Apple Watch) whose view may lag, so a repeated tap can't flip the state back.
     */
    fun pause() {
        _active.value?.engine?.pause()
    }

    /** Resumes the paused routine; does nothing if it is running. See [pause]. */
    fun resume() {
        _active.value?.engine?.resume()
    }

    fun skipStep() {
        _active.value?.engine?.skipStep()
    }

    /**
     * Carries out a voice assistant's or link's [command] (see [ExternalAction.ControlTimer]).
     * Returns false, doing nothing, when no routine is running.
     */
    fun control(command: TimerCommand): Boolean {
        if (_active.value == null) return false
        when (command) {
            TimerCommand.PAUSE -> pause()
            TimerCommand.RESUME -> resume()
            TimerCommand.SKIP -> skipStep()
            TimerCommand.END -> stop()
        }
        return true
    }

    private const val PROGRESS_REFRESH_SECONDS = 5
}

/** Platform-neutral view of the session for notifications and Live Activities. */
data class SessionSnapshot(
    val routineTitle: String,
    val stepTitle: String,
    val stepIndex: Int,
    val stepCount: Int,
    val nextStepTitle: String?,
    val stepDurationMs: Long,
    /** Epoch ms the step ends; null while paused. */
    val stepEndEpochMs: Long?,
    /** Remaining step time when the snapshot was taken (exact while paused). */
    val stepRemainingMs: Long,
    val stepDurationsSeconds: List<Int>,
    /** Routine time elapsed before the current step started. */
    val elapsedBeforeStepMs: Long,
    val isPaused: Boolean,
    val isCompleted: Boolean,
    /** The routine's accent colour (ARGB); null = the app's own. */
    val accentArgb: Long? = null,
    /** The current round, of [rounds]; both 1 for a routine without rounds. */
    val round: Int = 1,
    val rounds: Int = 1
) {
    val totalDurationMs: Long get() = stepDurationsSeconds.sumOf { it * 1000L }
}

/** [original] is the routine as saved, before [Routine.expandedForRounds] made the engine's steps. */
private fun TimerState.toSnapshot(original: Routine): SessionSnapshot {
    val position = original.roundPositionAt(currentStepIndex)
    val step = currentStep
    // The current step's segment includes any time added to it.
    val durations = routine.steps.mapIndexed { index, it ->
        if (index == currentStepIndex) (currentStepDurationMs / 1000).toInt() else it.durationSeconds
    }
    return SessionSnapshot(
        routineTitle = routine.title,
        stepTitle = step?.title.orEmpty(),
        stepIndex = currentStepIndex,
        stepCount = routine.steps.size,
        nextStepTitle = nextStep?.title,
        stepDurationMs = currentStepDurationMs,
        stepEndEpochMs = stepEndEpochMs,
        stepRemainingMs = if (stepEndEpochMs != null) secondsRemainingInStep * 1000L else pausedRemainingMs,
        stepDurationsSeconds = durations,
        elapsedBeforeStepMs = durations.take(currentStepIndex).sumOf { it * 1000L },
        isPaused = isPaused || !isRunning,
        isCompleted = isCompleted,
        accentArgb = routine.accentColorHex,
        round = position.round,
        rounds = position.rounds
    )
}

/** Shows the running session outside the app UI: notification, Live Activity, Dynamic Island. */
interface SessionPresenter {
    /** The notification / Live Activity; only for sessions started with background mode on. */
    fun show(snapshot: SessionSnapshot)

    /**
     * A companion device's own screen (the Apple Watch app), for every session whatever the
     * background mode: it shows what the phone app is doing rather than keeping it alive.
     */
    fun showOnCompanion(snapshot: SessionSnapshot) {}

    /** Clears everything [show] and [showOnCompanion] put up. */
    fun end()
}

expect fun createSessionPresenter(): SessionPresenter

/** Returns an action that asks for permission to post notifications (Android 13+, iOS). */
@Composable
expect fun rememberNotificationPermissionRequest(): () -> Unit
