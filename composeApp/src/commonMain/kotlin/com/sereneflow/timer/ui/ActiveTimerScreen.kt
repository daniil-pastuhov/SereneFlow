package com.sereneflow.timer.ui

import androidx.compose.ui.semantics.heading
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.session.ActiveSession
import com.sereneflow.timer.ui.platform.KeepScreenOn
import com.sereneflow.timer.ui.platform.rememberReduceMotion
import com.sereneflow.timer.engine.StepReminder
import com.sereneflow.timer.engine.nowEpochMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.physics.BonsaiTreeCanvas
import com.sereneflow.timer.physics.BreathingLotusCanvas
import com.sereneflow.timer.physics.CosmicOrbitCanvas
import com.sereneflow.timer.physics.HourglassCanvas
import com.sereneflow.timer.physics.WaterWaveCanvas
import com.sereneflow.timer.theme.LocalSereneColors
import com.sereneflow.timer.theme.accentColor
import com.sereneflow.timer.ui.platform.ConfirmDialog
import com.sereneflow.timer.resources.*
import com.sereneflow.timer.text.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ActiveTimerScreen(
    session: ActiveSession,
    keepScreenOn: Boolean,
    /** Whether the one-time voice commands tip hasn't been shown yet. */
    offerVoiceTip: Boolean,
    onVoiceTipShown: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSereneColors.current
    val routine = session.routine
    val engine = session.engine
    val state by engine.timerState.collectAsState()
    // The engine runs the rounds as one flat list; the rail and labels show the current round.
    val roundPosition = routine.roundPositionAt(state.currentStepIndex)
    val railIndex = if (routine.hasRounds) roundPosition.stepInRound ?: routine.steps.size else state.currentStepIndex
    // The routine's own colour leads the visual effect; the theme's otherwise.
    val canvasAccent = routine.accentColor ?: colors.primary

    KeepScreenOn(enabled = keepScreenOn && !state.isCompleted)

    // Read every frame inside the canvases' draw phase: exact progress from the wall-clock step
    // end, so visuals move continuously and stay correct after time in the background.
    val progress = { engine.timerState.value.stepProgressAt(nowEpochMs()) }

    // "% left" reminders also show for a few seconds, for anyone who can't hear them.
    // The last one stays set while the banner fades out.
    var reminder by remember { mutableStateOf<StepReminder?>(null) }
    var reminderVisible by remember { mutableStateOf(false) }
    LaunchedEffect(engine) {
        engine.reminders.collectLatest {
            reminder = it
            reminderVisible = true
            delay(REMINDER_BANNER_MS)
            reminderVisible = false
        }
    }

    // Voice commands are taught once ever: at the first pause, while it is the phrase that carries
    // on, or else when the first routine completes, with the phrase that starts it again. The pause
    // tip goes when the routine carries on, and isn't offered again.
    var voiceTip by remember { mutableStateOf<VoiceTipMoment?>(null) }
    LaunchedEffect(state.isPaused, state.isCompleted) {
        if (voiceTip == VoiceTipMoment.PAUSED && !state.isPaused) voiceTip = null
        if (!offerVoiceTip || voiceTip != null) return@LaunchedEffect
        voiceTip = when {
            state.isCompleted -> VoiceTipMoment.COMPLETED
            state.isPaused -> VoiceTipMoment.PAUSED
            else -> return@LaunchedEffect
        }
        onVoiceTipShown()
    }
    val pauseTip: (@Composable () -> Unit)? = if (voiceTip == VoiceTipMoment.PAUSED && state.isPaused) {
        {
            VoiceTipCard(
                text = stringResource(Res.string.voice_tip_paused, resumePhrase()),
                onDismiss = { voiceTip = null },
                modifier = Modifier.padding(top = 12.dp).widthIn(max = 420.dp).fillMaxWidth()
            )
        }
    } else null

    var showCloseDialog by remember { mutableStateOf(false) }
    val canCloseSilently = !state.isRunning || state.isCompleted
    val requestClose = { if (canCloseSilently) onClose() else showCloseDialog = true }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !canCloseSilently,
        onBackCompleted = { showCloseDialog = true }
    )

    if (showCloseDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.timer_end_title),
            message = stringResource(Res.string.timer_end_message, routine.title),
            confirmLabel = stringResource(Res.string.action_end),
            destructive = true,
            onConfirm = {
                showCloseDialog = false
                onClose()
            },
            onDismiss = { showCloseDialog = false }
        )
    }

    // Breathing pulse for timer aura; still with Reduce Motion.
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "TimerAura")
    val pulse by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraPulse"
    )
    val auraScale = if (reduceMotion) 1f else pulse
    val backdrop = rememberBackdropCapture()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .semantics { isTraversalGroup = true }
    ) {
        // Laid out from the space the screen has, never the device or orientation: folding an
        // iPhone Duo or resizing a window changes it mid-session. Wide windows (landscape, an
        // unfolded Duo on its side) put the title in a corner and the controls in a column beside
        // the dial, clear of the ring the canvases draw around the centre.
        val sideLayout = maxWidth > maxHeight * 1.15f
        // The dial was designed on a 440dp-wide phone; it grows and shrinks with the shorter side,
        // as the canvases' rings do.
        val dialScale = (minOf(maxWidth, maxHeight) / 440.dp).coerceIn(0.7f, 2f)
        // Controls grow less: touch targets need no more than a little extra on a tablet.
        val controlScale = dialScale.coerceIn(1f, 1.3f)
        // The ring effects draw a ring (or a lotus) around the centre that would run under the side
        // columns (title and step details, controls), so beside them they are centred on the space
        // between. The others fill the screen and stay clear of the columns on their own.
        val ringEffect = session.cues.visualEffect == VisualEffectType.COSMIC_ORBIT ||
            session.cues.visualEffect == VisualEffectType.BREATHING_LOTUS
        val safePadding = WindowInsets.safeDrawing.asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        val stageStartPadding = if (sideLayout && ringEffect) {
            safePadding.calculateStartPadding(layoutDirection) + 24.dp + SideColumnWidth + 16.dp
        } else {
            0.dp
        }
        val stageEndPadding = if (sideLayout && ringEffect) {
            safePadding.calculateEndPadding(layoutDirection) + 24.dp + 80.dp * controlScale + 16.dp
        } else {
            0.dp
        }

        // Under the completion overlay, nothing is left for a screen reader to reach.
        val hiddenWhenCompleted = if (state.isCompleted) Modifier.clearAndSetSemantics {} else Modifier

        val stepTitle = state.currentStep?.title ?: stringResource(Res.string.timer_step_fallback)
        val reminderText = reminder?.let { stringResource(Res.string.timer_reminder, it.percentLeft, it.stepTitle) }
        val showReminder = reminderVisible && !state.isCompleted
        val nextText = state.nextStep?.let { stringResource(Res.string.timer_up_next, it.title, shortDuration(it.durationSeconds)) }
        val nextDescription = state.nextStep?.let { stringResource(Res.string.timer_up_next_description, it.title, spokenDuration(it.durationSeconds)) }
        val onExtend: (() -> Unit)? = if (state.isCompleted) null else {
            { engine.extendStep(EXTEND_STEP_SECONDS * 1000L) }
        }
        // The top bar (title and rail, not the pause tip) and the controls, as laid out: the dial
        // keeps its step title and next step clear of both.
        val density = LocalDensity.current
        var topBarHeight by remember { mutableStateOf(0.dp) }
        var controlsHeight by remember { mutableStateOf(0.dp) }

        Box(modifier = Modifier.fillMaxSize().padding(start = stageStartPadding, end = stageEndPadding).then(hiddenWhenCompleted)) {
            // 1. Full-screen animated backdrop. Decorative: the dial below says the same in words.
            Box(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}.captureBackdrop(backdrop)) {
                when (session.cues.visualEffect) {
                    VisualEffectType.WATER -> {
                        WaterWaveCanvas(
                            progress = { 1f - progress() }, // Drains down gracefully
                            primaryColor = canvasAccent,
                            secondaryColor = colors.secondary
                        )
                    }
                    VisualEffectType.BONSAI_TREE -> {
                        BonsaiTreeCanvas(
                            progress = progress, // Blooms and grows
                            trunkColor = colors.onBackground.copy(alpha = 0.6f),
                            foliageColor = canvasAccent,
                            leafColor = colors.secondary
                        )
                    }
                    VisualEffectType.COSMIC_ORBIT -> {
                        CosmicOrbitCanvas(
                            progress = progress,
                            coreColor = canvasAccent,
                            accentColor = colors.secondary,
                            trackColor = colors.onBackground.copy(alpha = 0.12f)
                        )
                    }
                    VisualEffectType.BREATHING_LOTUS -> {
                        BreathingLotusCanvas(
                            progress = progress,
                            petalColor = canvasAccent,
                            waterColor = colors.secondary
                        )
                    }
                    // RANDOM never gets here: TimerSession.start picks one of the others.
                    VisualEffectType.HOURGLASS, VisualEffectType.RANDOM -> {
                        HourglassCanvas(
                            progress = progress,
                            glassColor = colors.onBackground,
                            sandColor = canvasAccent,
                            frameColor = lerp(colors.background, colors.onBackground, 0.45f)
                        )
                    }
                }
            }

            // 2. Centre: step title, countdown and next step, on the canvases' centre. Not inset, so
            // it lines up with the rings, which are drawn on the whole screen. A wide window has
            // no height to spare for the titles above and below, so there they move to the left
            // column and the countdown stands alone.
            TimerDial(
                stepTitle = stepTitle,
                remaining = state.formattedRemaining,
                // Screen readers: "Breathe, 1 minute 30 seconds left", never "01:30".
                remainingDescription = if (state.isCompleted) stringResource(Res.string.timer_complete_description) else
                    stringResource(Res.string.timer_time_left_description, stepTitle, spokenDuration(state.secondsRemainingInStep)),
                stepProgress = state.stepProgress,
                isPaused = state.isPaused,
                reminder = reminderText,
                showReminder = showReminder,
                next = nextText,
                nextDescription = nextDescription,
                scale = dialScale,
                auraScale = auraScale,
                backdrop = backdrop,
                onExtend = onExtend,
                countdownOnly = sideLayout,
                // The hourglass and the bonsai draw their subject behind the centre, where a disc
                // would hide the bulbs or the trunk; a pill that just fits the digits leaves them,
                // and the sand or the tree, in view.
                pillGlass = session.cues.visualEffect == VisualEffectType.HOURGLASS ||
                    session.cues.visualEffect == VisualEffectType.BONSAI_TREE,
                topInset = if (sideLayout) 0.dp else safePadding.calculateTopPadding() + 12.dp + topBarHeight + 8.dp,
                bottomInset = if (sideLayout) 0.dp else safePadding.calculateBottomPadding() + 12.dp + controlsHeight + 8.dp,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Title, progress rail and controls, inside the safe area.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(hiddenWhenCompleted)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            val muteButton: (@Composable () -> Unit)? = if (!session.cues.isSilent) {
                {
                    RoundIconButton(
                        icon = if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(if (state.isMuted) Res.string.timer_unmute else Res.string.timer_mute),
                        tint = if (state.isMuted) colors.onSurfaceVariant else colors.primary,
                        size = 42.dp,
                        iconSize = 20.dp,
                        onClick = { engine.toggleMute() }
                    )
                }
            } else null
            val title: @Composable (Alignment.Horizontal) -> Unit = { alignment ->
                Column(horizontalAlignment = alignment) {
                    Text(
                        text = routine.title,
                        modifier = Modifier.semantics { heading() },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground
                    )
                    Text(
                        text = if (routine.hasRounds) {
                            val step = roundPosition.stepInRound?.let {
                                stringResource(Res.string.timer_step_position, it + 1, routine.steps.size)
                            } ?: stringResource(Res.string.rest_step_title)
                            stringResource(Res.string.timer_round_position, roundPosition.round, roundPosition.rounds, step)
                        } else {
                            stringResource(Res.string.timer_step_position, state.currentStepIndex + 1, routine.steps.size)
                        },
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            val closeButton: @Composable () -> Unit = {
                RoundIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.action_close),
                    tint = colors.onSurface,
                    size = 42.dp,
                    onClick = requestClose
                )
            }
            val controls: @Composable () -> Unit = {
                val buttonSize = 52.dp * controlScale
                val iconSize = 24.dp * controlScale
                // One back button, as in media players: restarts the step, or goes back one when
                // pressed near its start. Its label follows, re-read as the state ticks.
                val rewindLabel = if (engine.rewindRestartsStep()) Res.string.timer_restart_step else Res.string.timer_previous_step
                RoundIconButton(Icons.Default.SkipPrevious, stringResource(rewindLabel), colors.onSurface, buttonSize, iconSize) { engine.rewind() }
                PlayPauseButton(
                    isPlaying = state.isRunning && !state.isPaused,
                    size = 80.dp * controlScale,
                    onClick = { engine.togglePlayPause() }
                )
                RoundIconButton(Icons.Default.SkipNext, stringResource(Res.string.timer_skip_step), colors.onSurface, buttonSize, iconSize) { engine.skipStep() }
            }

            if (sideLayout) {
                // Scrolls when a short window can't fit the pause tip, the status pills and the step
                // details at once, so "+30s" is never pushed out of reach.
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(SideColumnWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .then(TopBarFirst)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        closeButton()
                        Spacer(modifier = Modifier.width(12.dp))
                        title(Alignment.Start)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    StepRail(stepCount = routine.steps.size, currentIndex = railIndex)
                    Spacer(modifier = Modifier.height(24.dp))
                    StepDetails(
                        stepTitle = stepTitle,
                        isPaused = state.isPaused,
                        reminder = reminderText,
                        showReminder = showReminder,
                        next = nextText,
                        nextDescription = nextDescription,
                        scale = 1f,
                        onExtend = onExtend,
                        alignment = Alignment.Start
                    )
                    // The one-off voice tip goes last, so it never pushes the step out of view.
                    pauseTip?.invoke()
                }
                Column(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    muteButton?.invoke()
                    controls()
                }
            } else {
                Column(
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxWidth().then(TopBarFirst),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.onSizeChanged { topBarHeight = with(density) { it.height.toDp() } },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            closeButton()
                            title(Alignment.CenterHorizontally)
                            // Keeps the title centred when there is no sound to toggle.
                            muteButton?.invoke() ?: Spacer(modifier = Modifier.size(MinTouchTarget))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        StepRail(stepCount = routine.steps.size, currentIndex = railIndex)
                    }
                    pauseTip?.invoke()
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { controlsHeight = with(density) { it.height.toDp() } }
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp * controlScale, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    controls()
                }
            }
        }

        // 4. Completion Celebration Overlay
        AnimatedVisibility(
            visible = state.isCompleted,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut()
        ) {
            CelebrationOverlay(
                routine = routine,
                voiceTip = if (voiceTip == VoiceTipMoment.COMPLETED) {
                    stringResource(Res.string.voice_tip_completed, startPhrase(routine.title))
                } else null,
                onDismiss = onClose
            )
        }
    }
}

/**
 * The current step's title above the countdown and the next step below it, with the countdown
 * exactly in the middle of [modifier]'s area: equal space above and below keeps it on the centre
 * the canvases draw their rings around. With [countdownOnly] it is just the countdown, centred,
 * for layouts that show the rest beside it ([StepDetails]).
 */
@Composable
private fun TimerDial(
    stepTitle: String,
    remaining: String,
    /** What a screen reader says for the countdown. */
    remainingDescription: String,
    stepProgress: Float,
    isPaused: Boolean,
    /** A "% left" reminder that just fired, shown briefly. */
    reminder: String?,
    showReminder: Boolean,
    next: String?,
    nextDescription: String?,
    scale: Float,
    auraScale: Float,
    /** The animation behind the dial, shown frosted behind the countdown. */
    backdrop: BackdropCapture,
    /** Adds time to the current step; null hides the button. */
    onExtend: (() -> Unit)?,
    countdownOnly: Boolean,
    /**
     * Frosts a pill that just fits the digits instead of the round aura. The aura also gives way
     * to the pill when there is no room for it between the top bar and the controls.
     */
    pillGlass: Boolean,
    /**
     * Kept clear above and below for the top bar and the controls drawn over the dial. When the
     * step title and next step wouldn't fit between them otherwise, the countdown shrinks, and
     * past [MinCentredFit] moves off the centre as little as it must.
     */
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier
) {
    val colors = LocalSereneColors.current
    val pausedLabel = stringResource(Res.string.timer_paused)
    val gap = 16.dp * scale
    val density = LocalDensity.current
    val countdownSemantics = Modifier.clearAndSetSemantics {
        contentDescription = remainingDescription
        progressBarRangeInfo = ProgressBarRangeInfo(stepProgress, 0f..1f)
        if (isPaused) stateDescription = pausedLabel
    }
    val countdown: @Composable (Float, Boolean) -> Unit = { countdownScale, pill ->
        val auraSize = 240.dp * countdownScale
        // The numerals fill a fixed-size dial, so they are sized in dp: a large font scale would
        // otherwise clip them. They are already far larger than any text the font scale gives.
        val digits: @Composable (Modifier) -> Unit = { textModifier ->
            Text(
                text = remaining,
                modifier = textModifier,
                fontSize = with(density) { (68.dp * countdownScale).toSp() },
                fontWeight = FontWeight.Light,
                letterSpacing = with(density) { (3.dp * countdownScale).toSp() },
                color = colors.onBackground,
                softWrap = false
            )
        }
        if (pill) {
            digits(
                countdownSemantics
                    .countdownGlass(RoundedCornerShape(percent = 50), backdrop, countdownScale)
                    .padding(horizontal = 24.dp * countdownScale, vertical = 4.dp * countdownScale)
            )
        } else {
            Box(modifier = Modifier.size(auraSize).then(countdownSemantics), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(auraSize * auraScale).countdownGlass(CircleShape, backdrop, countdownScale))
                digits(Modifier)
            }
        }
    }

    if (countdownOnly) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { countdown(scale, pillGlass) }
        return
    }
    SubcomposeLayout(modifier = modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gapPx = gap.roundToPx()
        val title = subcompose(DialSlot.Title) { StepTitlePill(stepTitle, scale) }.map { it.measure(loose) }
        val status = subcompose(DialSlot.Status) {
            StepStatus(isPaused, reminder, showReminder, next, nextDescription, scale, onExtend, Alignment.CenterHorizontally, inRow = true)
        }.map { it.measure(loose) }
        // Room is kept for the status as it is while paused, measured but not placed, so pausing
        // doesn't move the countdown.
        val pausedStatusHeight = subcompose(DialSlot.PausedStatus) {
            StepStatus(true, null, false, next, nextDescription, scale, onExtend, Alignment.CenterHorizontally, inRow = true)
        }.maxOfOrNull { it.measure(loose).height } ?: 0
        val above = (title.maxOfOrNull { it.height } ?: 0) + gapPx
        val below = maxOf(pausedStatusHeight, status.maxOfOrNull { it.height } ?: 0) + gapPx

        // The free space between the top bar and the controls, and the canvases' centre in it.
        val top = topInset.roundToPx()
        val bottom = (height - bottomInset.roundToPx()).coerceAtLeast(top)
        val centre = height / 2
        // How much the countdown, as tall as this, may be shrunk to stay on the centre.
        fun centredFit(dialHeight: Int) =
            2f * minOf(centre - top - above, bottom - centre - below) / dialHeight.coerceAtLeast(1)

        // A disc too big to stay on the centre gives way to the pill, which is far shorter.
        val roundDial = if (pillGlass) null else {
            subcompose(DialSlot.Countdown) { countdown(scale, false) }.map { it.measure(loose) }
        }
        val pill = roundDial == null || centredFit(roundDial.maxOfOrNull { it.height } ?: 0) < MinCentredFit
        val fullDial = if (!pill) roundDial!! else {
            subcompose(DialSlot.PillCountdown) { countdown(scale, true) }.map { it.measure(loose) }
        }
        val fullHeight = fullDial.maxOfOrNull { it.height }?.coerceAtLeast(1) ?: 1
        // Shrinks to stay on the centre, down to MinCentredFit; then moves off it, and only
        // shrinks further when the whole column doesn't fit.
        val fit = centredFit(fullHeight).let { centred ->
            if (centred >= MinCentredFit) centred.coerceAtMost(1f) else {
                ((bottom - top - above - below).toFloat() / fullHeight).coerceIn(MinCountdownFit, MinCentredFit)
            }
        }
        val dial = if (fit >= 1f) fullDial else {
            subcompose(DialSlot.FittedCountdown) { countdown(scale * fit, pill) }.map { it.measure(loose) }
        }
        val dialHeight = dial.maxOfOrNull { it.height } ?: 0
        val dialTop = (centre - dialHeight / 2)
            .coerceAtMost(bottom - below - dialHeight)
            .coerceAtLeast(top + above)

        layout(width, height) {
            dial.forEach { it.place((width - it.width) / 2, dialTop + (dialHeight - it.height) / 2) }
            title.forEach { it.place((width - it.width) / 2, dialTop - gapPx - it.height) }
            status.forEach { it.place((width - it.width) / 2, dialTop + dialHeight + gapPx) }
        }
    }
}

private enum class DialSlot { Title, Status, PausedStatus, Countdown, PillCountdown, FittedCountdown }

/** The smallest the countdown shrinks to while it stays on the canvases' centre. */
private const val MinCentredFit = 0.7f

/** The smallest the countdown shrinks to at all, when even off the centre there is no room. */
private const val MinCountdownFit = 0.5f

/**
 * Frosted glass behind the countdown: the animation behind it is blurred and veiled, so the
 * digits stay readable whatever the effect draws there. The opaque base comes first: the
 * canvases draw on a transparent background, so without it the sharp animation would show
 * through the blur.
 */
@Composable
private fun Modifier.countdownGlass(shape: Shape, backdrop: BackdropCapture, scale: Float): Modifier {
    val colors = LocalSereneColors.current
    return clip(shape)
        .background(colors.background)
        .frostedBackdrop(backdrop, radius = 20.dp * scale)
        .background(colors.background.copy(alpha = 0.35f))
        .background(colors.accentGlow)
}

/**
 * The step title, status, next step and "+30s" in one column, for layouts that show them beside
 * the countdown rather than above and below it.
 */
@Composable
private fun StepDetails(
    stepTitle: String,
    isPaused: Boolean,
    reminder: String?,
    showReminder: Boolean,
    next: String?,
    nextDescription: String?,
    scale: Float,
    onExtend: (() -> Unit)?,
    alignment: Alignment.Horizontal
) {
    Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepTitlePill(stepTitle, scale)
        StepStatus(isPaused, reminder, showReminder, next, nextDescription, scale, onExtend, alignment)
    }
}

/**
 * The current step's title. Announced when the step changes; the countdown is not, or it would
 * be read out every second.
 */
@Composable
private fun StepTitlePill(stepTitle: String, scale: Float) {
    val colors = LocalSereneColors.current
    Box(
        modifier = Modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface.copy(alpha = 0.88f))
            .border(1.dp, colors.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        Text(
            text = stepTitle,
            fontSize = 16.sp * scale.coerceAtMost(1.4f),
            fontWeight = FontWeight.Medium,
            color = colors.onSurface
        )
    }
}

/**
 * Paused and reminder status, the next step and "+30s", under the countdown or beside it. Under
 * it, [inRow] lines them up side by side, wrapping when they don't fit, to leave the countdown
 * room between the top bar and the controls.
 */
@Composable
private fun StepStatus(
    isPaused: Boolean,
    reminder: String?,
    showReminder: Boolean,
    next: String?,
    nextDescription: String?,
    scale: Float,
    onExtend: (() -> Unit)?,
    alignment: Alignment.Horizontal,
    inRow: Boolean = false
) {
    val colors = LocalSereneColors.current
    val spacing = Arrangement.spacedBy(10.dp, alignment)
    val container: @Composable (@Composable () -> Unit) -> Unit = if (inRow) {
        { content ->
            FlowRow(
                horizontalArrangement = spacing,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) { content() }
        }
    } else {
        { content -> Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
    }
    container {
        // Seen as well as heard: pausing, and each "% left" reminder.
        if (isPaused) {
            StatusPill(text = stringResource(Res.string.timer_paused), scale = scale)
        }
        AnimatedVisibility(visible = showReminder && reminder != null, enter = fadeIn(), exit = fadeOut()) {
            StatusPill(text = reminder.orEmpty(), scale = scale)
        }

        // Up Next Step Banner
        if (next != null) {
            Box(
                modifier = Modifier
                    .clearAndSetSemantics { contentDescription = nextDescription ?: next }
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface.copy(alpha = 0.75f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = next,
                    fontSize = 12.sp * scale.coerceAtMost(1.4f),
                    color = colors.onSurfaceVariant
                )
            }
        }
        if (onExtend != null) ExtendStepButton(onClick = onExtend)
    }
}

/** A short status line under the countdown, announced when it appears. */
@Composable
private fun StatusPill(text: String, scale: Float) {
    val colors = LocalSereneColors.current
    Text(
        text = text,
        fontSize = 13.sp * scale.coerceAtMost(1.4f),
        fontWeight = FontWeight.SemiBold,
        color = colors.primary,
        modifier = Modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

/** "+30s": gives the current step a little more time. A small pill with a 48dp touch target. */
@Composable
private fun ExtendStepButton(onClick: () -> Unit) {
    val colors = LocalSereneColors.current
    val description = pluralStringResource(Res.plurals.timer_extend_description, EXTEND_STEP_SECONDS, EXTEND_STEP_SECONDS)
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                onClickLabel = pluralStringResource(Res.plurals.timer_extend_action, EXTEND_STEP_SECONDS, EXTEND_STEP_SECONDS),
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(Res.string.timer_extend_label, EXTEND_STEP_SECONDS),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface.copy(alpha = 0.85f))
                .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .semantics { contentDescription = description }
        )
    }
}

private const val EXTEND_STEP_SECONDS = 30

/** Width of the title and step details column in wide layouts. */
private val SideColumnWidth = 240.dp

/** When the voice commands tip shows. */
private enum class VoiceTipMoment { PAUSED, COMPLETED }

/** How long a "% left" reminder stays on screen. */
private const val REMINDER_BANNER_MS = 4_000L

/**
 * Screen readers start at the title and Close, before the dial that is drawn first and the
 * controls, whatever the layout.
 */
private val TopBarFirst = Modifier.semantics {
    isTraversalGroup = true
    traversalIndex = -1f
}

/** The smallest touch target either platform's guidelines allow. */
private val MinTouchTarget = 48.dp

/**
 * Segmented progress rail: one segment per step, filled up to the current one, which is taller so
 * it doesn't depend on colour alone. Hidden from screen readers: the title says "Step N of M".
 */
@Composable
private fun StepRail(stepCount: Int, currentIndex: Int) {
    val colors = LocalSereneColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(stepCount) { idx ->
            val segmentColor = when {
                idx < currentIndex -> colors.primary
                idx == currentIndex -> colors.primary.copy(alpha = 0.85f)
                else -> colors.surfaceVariant.copy(alpha = 0.45f)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (idx == currentIndex) 9.dp else 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(segmentColor)
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    size: Dp = 52.dp,
    iconSize: Dp = 24.dp,
    onClick: () -> Unit
) {
    val colors = LocalSereneColors.current
    // At least a 48dp touch target, even when the circle drawn is smaller.
    IconButton(onClick = onClick, modifier = Modifier.size(maxOf(size, MinTouchTarget))) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.surface.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, size: Dp, onClick: () -> Unit) {
    val colors = LocalSereneColors.current
    Box(
        modifier = Modifier
            .size(size)
            .shadow(elevation = 12.dp, shape = CircleShape, spotColor = colors.primary)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(colors.primary, colors.secondary)
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(if (isPlaying) Res.string.timer_pause else Res.string.timer_play),
            tint = colors.onPrimary,
            modifier = Modifier.size(size * 0.475f)
        )
    }
}

@Composable
private fun CelebrationOverlay(
    routine: Routine,
    /** The one-time voice commands tip, or null. */
    voiceTip: String?,
    onDismiss: () -> Unit
) {
    val colors = LocalSereneColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            // A tap anywhere dismisses it too. Not a button for screen readers, which would merge
            // the whole card into it; they get the Return button.
            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surface)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(28.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "✨",
                fontSize = 44.sp,
                modifier = Modifier.clearAndSetSemantics {}
            )

            Text(
                text = stringResource(Res.string.timer_complete_title),
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface
            )

            Text(
                text = stringResource(Res.string.timer_complete_message, routine.title, totalDuration(routine.totalDurationSeconds)),
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (voiceTip != null) {
                VoiceTipCard(text = voiceTip, onDismiss = null, modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.primary)
                    .clickable(role = Role.Button) { onDismiss() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.timer_return),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimary
                )
            }
        }
    }
}
