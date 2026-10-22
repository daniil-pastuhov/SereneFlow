package com.sereneflow.timer.engine

import com.sereneflow.timer.audio.AudioController
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.model.SoundMode
import com.sereneflow.timer.model.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val audioController: AudioController,
    reminderThresholds: Set<Int> = emptySet(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
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

    private var timerJob: Job? = null

    // Highest threshold first so "50% left" fires before "25% left" if both are crossed in one tick.
    private val reminderPercents = reminderThresholds.filter { it in 1..99 }.sortedDescending()
    private val announcedReminders = mutableSetOf<Int>()

    private val isSilent: Boolean
        get() = routine.soundMode == SoundMode.MUTE || _timerState.value.isMuted

    private val isTicking: Boolean
        get() = _timerState.value.let { it.isRunning && !it.isPaused }

    fun start() {
        if (isTicking || routine.steps.isEmpty()) return
        val state = _timerState.value
        _timerState.value = state.copy(
            isRunning = true,
            isPaused = false,
            isCompleted = false,
            stepEndEpochMs = clock() + state.pausedRemainingMs
        )
        announceStepStart(state.currentStepIndex)
        startTickerLoop()
    }

    fun pause() {
        if (!isTicking) return
        timerJob?.cancel()
        sync()
        val state = _timerState.value
        if (state.isCompleted) return
        _timerState.value = state.copy(
            isPaused = true,
            pausedRemainingMs = state.stepRemainingMsAt(clock()),
            stepEndEpochMs = null
        )
        audioController.stop()
    }

    fun resume() {
        val state = _timerState.value
        if (!state.isRunning || !state.isPaused) return
        _timerState.value = state.copy(
            isPaused = false,
            stepEndEpochMs = clock() + state.pausedRemainingMs
        )
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
        _timerState.value = _timerState.value.copy(isMuted = muted)
        if (muted) audioController.stop()
    }

    fun toggleMute() = setMuted(!_timerState.value.isMuted)

    /**
     * Moves a ticking timer [ms] further on, as if that much time had passed (demo launches). A
     * timer that isn't ticking has no step end, so it stays where it is.
     */
    fun fastForward(ms: Long) {
        val state = _timerState.value
        _timerState.value = state.copy(stepEndEpochMs = state.stepEndEpochMs?.minus(ms))
        sync()
    }

    fun restartStep() {
        transitionToStep(_timerState.value.currentStepIndex, announceWhenPaused = true)
    }

    fun stop() {
        timerJob?.cancel()
        audioController.stop()
        val state = _timerState.value
        _timerState.value = state.copy(
            isRunning = false,
            isPaused = false,
            pausedRemainingMs = state.stepRemainingMsAt(clock()),
            stepEndEpochMs = null
        )
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
            _timerState.value = state.copy(
                currentStepIndex = index,
                stepEndEpochMs = stepEnd,
                secondsRemainingInStep = routine.steps[index].durationSeconds
            )
            // Only the step we landed on is announced, even if several were skipped while suspended.
            announceStepStart(index)
        }

        val step = routine.steps[index]
        val previousSeconds = _timerState.value.secondsRemainingInStep
        val remainingSeconds = ceilSeconds(stepEnd - now)
        if (remainingSeconds == previousSeconds) return

        _timerState.value = _timerState.value.copy(secondsRemainingInStep = remainingSeconds)
        checkReminders(step, remainingSeconds)

        // 3, 2, 1 countdown chime, only when the second was reached live (not after a long gap).
        val liveTick = previousSeconds - remainingSeconds == 1
        if (liveTick && remainingSeconds in 1..3 && !isSilent) {
            audioController.playZenChime(frequencyHz = 520.0 + (3 - remainingSeconds) * 60, durationMs = 300)
        }
    }

    private fun transitionToStep(index: Int, announceWhenPaused: Boolean = false) {
        val step = routine.steps.getOrNull(index) ?: return
        announcedReminders.clear()
        val state = _timerState.value
        _timerState.value = state.copy(
            currentStepIndex = index,
            secondsRemainingInStep = step.durationSeconds,
            stepEndEpochMs = if (isTicking) clock() + step.durationMs else null,
            pausedRemainingMs = step.durationMs
        )
        if (isTicking || (announceWhenPaused && state.isRunning)) {
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
        _timerState.value = _timerState.value.copy(
            secondsRemainingInStep = 0,
            stepEndEpochMs = null,
            pausedRemainingMs = 0,
            isRunning = false,
            isPaused = false,
            isCompleted = true
        )

        if (_timerState.value.isMuted) return
        when (routine.soundMode) {
            SoundMode.VOICE_AND_CHIME -> {
                audioController.playRoutineComplete()
                audioController.speak("Routine complete. Well done.")
            }
            SoundMode.VOICE_ONLY -> {
                audioController.speak("Routine complete. Wonderful work.")
            }
            SoundMode.TIBETAN_BOWL, SoundMode.NATURE_SOUND -> {
                audioController.playRoutineComplete()
            }
            SoundMode.MUTE -> {}
        }
    }

    private fun announceStepStart(stepIndex: Int) {
        val step = routine.steps.getOrNull(stepIndex) ?: return
        if (_timerState.value.isMuted) return
        when (routine.soundMode) {
            SoundMode.VOICE_AND_CHIME -> {
                audioController.playTibetanBowl()
                audioController.speak("${step.title}, ${step.formattedDuration}")
            }
            SoundMode.VOICE_ONLY -> {
                audioController.speak("Step ${stepIndex + 1}: ${step.title}, ${step.formattedDuration}")
            }
            SoundMode.TIBETAN_BOWL -> {
                audioController.playTibetanBowl()
            }
            SoundMode.NATURE_SOUND -> {
                audioController.playZenChime()
            }
            SoundMode.MUTE -> {}
        }
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
        announceReminder(step.title, crossed.min())
    }

    private fun announceReminder(stepTitle: String, percentLeft: Int) {
        if (isSilent) return
        when (routine.soundMode) {
            SoundMode.VOICE_AND_CHIME -> {
                audioController.playZenChime()
                audioController.speak("$stepTitle, $percentLeft percent left")
            }
            SoundMode.VOICE_ONLY -> audioController.speak("$stepTitle, $percentLeft percent left")
            SoundMode.TIBETAN_BOWL, SoundMode.NATURE_SOUND -> audioController.playZenChime()
            SoundMode.MUTE -> {}
        }
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

/** Current wall-clock time in epoch milliseconds. */
fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()
