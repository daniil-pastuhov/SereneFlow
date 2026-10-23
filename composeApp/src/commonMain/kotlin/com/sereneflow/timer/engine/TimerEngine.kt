package com.sereneflow.timer.engine

import com.sereneflow.timer.audio.AudioController
import com.sereneflow.timer.audio.HapticKind
import com.sereneflow.timer.audio.ResourcePhrases
import com.sereneflow.timer.audio.SpokenPhrases
import com.sereneflow.timer.audio.playStepCue
import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineCues
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.model.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Routine timer driven by wall-clock time rather than by counting ticks.
 *
 * Each running step has an absolute end time; the ticker only re-reads the clock. If the process
 * is suspended (app in background, CPU asleep) and ticks are missed, the next [sync] catches up
 * through every step that ended in the meantime, so the timer is always correct when it resumes.
 */
class TimerEngine(
    private val routine: Routine,
    /** The routine's sound and voice, with its Default choices already filled in. */
    private val cues: RoutineCues,
    private val audioController: AudioController,
    reminderThresholds: Set<Int> = emptySet(),
    /** Vibrate on step start, the 3-2-1 countdown and completion (mute leaves these on). */
    private val haptics: Boolean = false,
    /** The words spoken for steps, reminders and completion. */
    private val phrases: SpokenPhrases = ResourcePhrases,
    /** Main by default, the thread the UI and notification controls call in on, so state has one writer. */
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate),
    private val clock: () -> Long = ::nowEpochMs
) {
    private val _timerState = MutableStateFlow(
        TimerState(
            routine = routine,
            currentStepIndex = 0,
            secondsRemainingInStep = routine.steps.firstOrNull()?.durationSeconds ?: 0,
            pausedRemainingMs = routine.steps.firstOrNull()?.durationMs ?: 0
        )
    )
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _reminders = MutableSharedFlow<StepReminder>(extraBufferCapacity = 4)

    /**
     * Each "% left" reminder as it fires, sound or not, for an on-screen banner: the cue for
     * anyone who can't hear it or has the routine muted.
     */
    val reminders: SharedFlow<StepReminder> = _reminders.asSharedFlow()

    private var timerJob: Job? = null

    // Highest threshold first so "50% left" fires before "25% left" if both are crossed in one tick.
    private val reminderPercents = reminderThresholds.filter { it in 1..99 }.sortedDescending()
    private val announcedReminders = mutableSetOf<Int>()

    private val isSilent: Boolean
        get() = cues.isSilent || _timerState.value.isMuted

    private val isTicking: Boolean
        get() = _timerState.value.let { it.isRunning && !it.isPaused }

    fun start() {
        if (isTicking || routine.steps.isEmpty()) return
        val now = clock()
        _timerState.update {
            it.copy(
                isRunning = true,
                isPaused = false,
                isCompleted = false,
                stepEndEpochMs = now + it.pausedRemainingMs
            )
        }
        announceStepStart(_timerState.value.currentStepIndex)
        startTickerLoop()
    }

    fun pause() {
        if (!isTicking) return
        timerJob?.cancel()
        sync()
        if (_timerState.value.isCompleted) return
        val now = clock()
        _timerState.update {
            it.copy(isPaused = true, pausedRemainingMs = it.stepRemainingMsAt(now), stepEndEpochMs = null)
        }
        audioController.stop()
    }

    fun resume() {
        val state = _timerState.value
        if (!state.isRunning || !state.isPaused) return
        val now = clock()
        _timerState.update { it.copy(isPaused = false, stepEndEpochMs = now + it.pausedRemainingMs) }
        startTickerLoop()
    }

    fun togglePlayPause() {
        val state = _timerState.value
        when {
            !state.isRunning -> start()
            state.isPaused -> resume()
            else -> pause()
        }
    }

    fun skipStep() {
        val nextIdx = _timerState.value.currentStepIndex + 1
        if (nextIdx < routine.steps.size) {
            transitionToStep(nextIdx)
        } else {
            completeRoutine()
        }
    }

    fun previousStep() {
        val prevIdx = (_timerState.value.currentStepIndex - 1).coerceAtLeast(0)
        transitionToStep(prevIdx)
    }

    fun setMuted(muted: Boolean) {
        _timerState.update { it.copy(isMuted = muted) }
        if (muted) audioController.stop()
    }

    fun toggleMute() = setMuted(!_timerState.value.isMuted)

    /**
     * Moves a ticking timer [ms] further on, as if that much time had passed (demo launches). A
     * timer that isn't ticking has no step end, so it stays where it is.
     */
    fun fastForward(ms: Long) {
        _timerState.update { it.copy(stepEndEpochMs = it.stepEndEpochMs?.minus(ms)) }
        sync()
    }

    /**
     * Gives the current step [ms] more time ("+30s"): a ticking step's end moves later, a paused or
     * not-yet-started one gets more remaining time. Nothing once the routine is complete.
     */
    fun extendStep(ms: Long) {
        if (ms <= 0) return
        val state = _timerState.value
        if (state.isCompleted || state.currentStep == null) return
        val now = clock()
        _timerState.update {
            val extended = it.copy(
                stepEndEpochMs = it.stepEndEpochMs?.plus(ms),
                pausedRemainingMs = if (it.stepEndEpochMs == null) it.pausedRemainingMs + ms else it.pausedRemainingMs,
                stepExtensionMs = it.stepExtensionMs + ms
            )
            extended.copy(secondsRemainingInStep = ceilSeconds(extended.stepRemainingMsAt(now)))
        }
    }

    fun restartStep() {
        transitionToStep(_timerState.value.currentStepIndex, announceWhenPaused = true)
    }

    fun stop() {
        timerJob?.cancel()
        audioController.stop()
        val now = clock()
        _timerState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                pausedRemainingMs = it.stepRemainingMsAt(now),
                stepEndEpochMs = null
            )
        }
    }

    /**
     * Re-reads the clock and applies everything that should have happened since the last call:
     * step changes, reminders, countdown chimes and completion. Called by the ticker, and can be
     * called directly when the app returns to the foreground.
     */
    fun sync() {
        val state = _timerState.value
        if (!state.isRunning || state.isPaused) return
        var stepEnd = state.stepEndEpochMs ?: return
        val now = clock()

        // Catch up through any steps that ended while we weren't ticking.
        var index = state.currentStepIndex
        while (now >= stepEnd) {
            index++
            if (index >= routine.steps.size) {
                completeRoutine()
                return
            }
            stepEnd += routine.steps[index].durationMs
        }
        if (index != state.currentStepIndex) {
            announcedReminders.clear()
            val newEnd = stepEnd
            _timerState.update {
                it.copy(
                    currentStepIndex = index,
                    stepEndEpochMs = newEnd,
                    secondsRemainingInStep = routine.steps[index].durationSeconds,
                    stepExtensionMs = 0
                )
            }
            // Only the step we landed on is announced, even if several were skipped while suspended.
            announceStepStart(index)
        }

        val step = routine.steps[index]
        val previousSeconds = _timerState.value.secondsRemainingInStep
        val remainingSeconds = ceilSeconds(stepEnd - now)
        if (remainingSeconds == previousSeconds) return

        _timerState.update { it.copy(secondsRemainingInStep = remainingSeconds) }
        checkReminders(step, remainingSeconds)

        // 3, 2, 1 countdown chime, only when the second was reached live (not after a long gap).
        val liveTick = previousSeconds - remainingSeconds == 1
        if (liveTick && remainingSeconds in 1..3) {
            if (!isSilent) {
                audioController.playZenChime(frequencyHz = 520.0 + (3 - remainingSeconds) * 60, durationMs = 300)
            }
            if (haptics) audioController.haptic(HapticKind.COUNTDOWN_TICK)
        }
    }

    private fun transitionToStep(index: Int, announceWhenPaused: Boolean = false) {
        val step = routine.steps.getOrNull(index) ?: return
        announcedReminders.clear()
        val state = _timerState.value
        val ticking = isTicking
        val stepEnd = if (ticking) clock() + step.durationMs else null
        _timerState.update {
            it.copy(
                currentStepIndex = index,
                secondsRemainingInStep = step.durationSeconds,
                stepEndEpochMs = stepEnd,
                pausedRemainingMs = step.durationMs,
                stepExtensionMs = 0
            )
        }
        if (ticking || (announceWhenPaused && state.isRunning)) {
            announceStepStart(index)
        }
    }

    private fun startTickerLoop() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && isTicking) {
                delay(TICK_MS)
                sync()
            }
        }
    }

    private fun completeRoutine() {
        timerJob?.cancel()
        _timerState.update {
            it.copy(
                secondsRemainingInStep = 0,
                stepEndEpochMs = null,
                pausedRemainingMs = 0,
                isRunning = false,
                isPaused = false,
                isCompleted = true
            )
        }

        if (haptics) audioController.haptic(HapticKind.ROUTINE_COMPLETE)
        if (_timerState.value.isMuted) return
        if (cues.sound != CueSound.NONE) audioController.playRoutineComplete()
        if (cues.voice) sound { audioController.speak(phrases.routineComplete()) }
    }

    private fun announceStepStart(stepIndex: Int) {
        val step = routine.steps.getOrNull(stepIndex) ?: return
        if (haptics) audioController.haptic(HapticKind.STEP_START)
        if (_timerState.value.isMuted) return
        sound { audioController.playStepCue(cues.sound, cues.voice, step, stepIndex, phrases) }
    }

    /**
     * Runs [cue] at once, up to the point it waits for words from resources (normally cached, so
     * not at all): tones play on time, and speech follows as soon as its phrase is read.
     */
    private fun sound(cue: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { cue() }
    }

    /**
     * Fires each configured "% left" reminder once per step, when the remaining share of the step
     * drops to or below the threshold. Steps shorter than [MIN_REMINDER_STEP_SECONDS] are skipped
     * so short steps don't get a burst of announcements on top of the countdown chimes.
     */
    private fun checkReminders(step: RoutineStep, remainingSeconds: Int) {
        if (step.durationSeconds < MIN_REMINDER_STEP_SECONDS || remainingSeconds <= 0) return
        val crossed = reminderPercents.filter { pct ->
            pct !in announcedReminders && remainingSeconds * 100 <= step.durationSeconds * pct
        }
        if (crossed.isEmpty()) return
        announcedReminders += crossed
        // Announce only the lowest crossed threshold; it is the most current information.
        _reminders.tryEmit(StepReminder(step.title, crossed.min()))
        announceReminder(step.title, crossed.min())
    }

    private fun announceReminder(stepTitle: String, percentLeft: Int) {
        if (isSilent) return
        if (cues.sound != CueSound.NONE) audioController.playZenChime()
        if (cues.voice) sound { audioController.speak(phrases.reminder(stepTitle, percentLeft)) }
    }

    private val RoutineStep.durationMs: Long
        get() = durationSeconds * 1000L

    private fun ceilSeconds(ms: Long): Int = ((ms + 999) / 1000).toInt()

    companion object {
        const val MIN_REMINDER_STEP_SECONDS = 10

        /** Clock re-read interval; sub-second so displayed seconds flip close to the real boundary. */
        private const val TICK_MS = 200L
    }
}

/** A "% left" reminder: [percentLeft] of the step called [stepTitle] remains. */
data class StepReminder(val stepTitle: String, val percentLeft: Int)

/** Current wall-clock time in epoch milliseconds. */
fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()
