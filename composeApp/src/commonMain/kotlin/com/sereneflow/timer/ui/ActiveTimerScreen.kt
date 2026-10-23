package com.sereneflow.timer.ui

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.sereneflow.timer.model.spokenDuration
import com.sereneflow.timer.session.ActiveSession
import com.sereneflow.timer.ui.platform.KeepScreenOn
import com.sereneflow.timer.ui.platform.rememberReduceMotion
import com.sereneflow.timer.engine.nowEpochMs
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.physics.BonsaiTreeCanvas
import com.sereneflow.timer.physics.BreathingLotusCanvas
import com.sereneflow.timer.physics.CosmicOrbitCanvas
import com.sereneflow.timer.physics.HourglassCanvas
import com.sereneflow.timer.physics.WaterWaveCanvas
import com.sereneflow.timer.theme.LocalSereneColors
import com.sereneflow.timer.theme.accentColor
import com.sereneflow.timer.ui.platform.ConfirmDialog

@Composable
fun ActiveTimerScreen(
    session: ActiveSession,
    keepScreenOn: Boolean,
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
            title = "End routine?",
            message = "Your progress in \"${routine.title}\" won't be kept.",
            confirmLabel = "End",
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
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
        // The ring effects draw a ring around the centre that would run under the side column of
        // controls, so beside it they are centred on the space that is left. The others fill the
        // screen and stay clear of the column on their own.
        val ringEffect = session.cues.visualEffect == VisualEffectType.COSMIC_ORBIT ||
            session.cues.visualEffect == VisualEffectType.BREATHING_LOTUS
        val stageEndPadding = if (sideLayout && ringEffect) {
            val safeEnd = WindowInsets.safeDrawing.asPaddingValues().calculateEndPadding(LocalLayoutDirection.current)
            safeEnd + 24.dp + 80.dp * controlScale + 16.dp
        } else {
            0.dp
        }

        Box(modifier = Modifier.fillMaxSize().padding(end = stageEndPadding)) {
            // 1. Full-screen animated backdrop. Decorative: the dial below says the same in words.
            Box(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}) {
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
                            goldAccent = colors.secondary,
                            trackColor = colors.onBackground.copy(alpha = 0.12f)
                        )
                    }
                    VisualEffectType.HOURGLASS -> {
                        HourglassCanvas(
                            progress = progress,
                            glassColor = colors.surfaceVariant,
                            sandColor = canvasAccent,
                            accentColor = colors.secondary
                        )
                    }
                }
            }

            // 2. Centre: step title, countdown and next step, on the canvases' centre. Not inset, so
            // it lines up with the rings, which are drawn on the whole screen.
            val stepTitle = state.currentStep?.title ?: "Routine Step"
            TimerDial(
                stepTitle = stepTitle,
                remaining = state.formattedRemaining,
                // Screen readers: "Breathe, 1 minute 30 seconds left", never "01:30".
                remainingDescription = if (state.isCompleted) "Routine complete" else
                    "$stepTitle, ${spokenDuration(state.secondsRemainingInStep)} left",
                stepProgress = state.stepProgress,
                isPaused = state.isPaused,
                next = state.nextStep?.let { "Up next: ${it.title} (${it.formattedDuration})" },
                nextDescription = state.nextStep?.let { "Up next: ${it.title}, ${it.spokenDuration}" },
                scale = dialScale,
                auraScale = auraScale,
                onExtend = if (state.isCompleted) null else {
                    { engine.extendStep(EXTEND_STEP_SECONDS * 1000L) }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Title, progress rail and controls, inside the safe area.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            val muteButton: (@Composable () -> Unit)? = if (!session.cues.isSilent) {
                {
                    RoundIconButton(
                        icon = if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (state.isMuted) "Unmute" else "Mute",
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground
                    )
                    Text(
                        text = if (routine.hasRounds) {
                            val step = roundPosition.stepInRound?.let { "Step ${it + 1} of ${routine.steps.size}" } ?: "Rest"
                            "Round ${roundPosition.round} of ${roundPosition.rounds} · $step"
                        } else {
                            "Step ${state.currentStepIndex + 1} of ${routine.steps.size}"
                        },
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            val closeButton: @Composable () -> Unit = {
                RoundIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = colors.onSurface,
                    size = 42.dp,
                    onClick = requestClose
                )
            }
            val controls: @Composable () -> Unit = {
                val buttonSize = 52.dp * controlScale
                val iconSize = 24.dp * controlScale
                RoundIconButton(Icons.Default.Refresh, "Restart Step", colors.onSurface, buttonSize, iconSize) { engine.restartStep() }
                RoundIconButton(Icons.Default.FastRewind, "Previous Step", colors.onSurface, buttonSize, iconSize) { engine.previousStep() }
                PlayPauseButton(
                    isPlaying = state.isRunning && !state.isPaused,
                    size = 80.dp * controlScale,
                    onClick = { engine.togglePlayPause() }
                )
                RoundIconButton(Icons.Default.FastForward, "Skip Step", colors.onSurface, buttonSize, iconSize) { engine.skipStep() }
            }

            if (sideLayout) {
                Column(modifier = Modifier.align(Alignment.TopStart).width(240.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        closeButton()
                        Spacer(modifier = Modifier.width(12.dp))
                        title(Alignment.Start)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    StepRail(stepCount = routine.steps.size, currentIndex = railIndex)
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
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxWidth(),
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
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 480.dp * controlScale)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
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
                onDismiss = onClose
            )
        }
    }
}

/**
 * The current step's title above the countdown and the next step below it, with the countdown
 * exactly in the middle of [modifier]'s area: equal space above and below keeps it on the centre
 * the canvases draw their rings around.
 */
@Composable
private fun TimerDial(
    stepTitle: String,
    remaining: String,
    /** What a screen reader says for the countdown. */
    remainingDescription: String,
    stepProgress: Float,
    isPaused: Boolean,
    next: String?,
    nextDescription: String?,
    scale: Float,
    auraScale: Float,
    /** Adds time to the current step; null hides the button. */
    onExtend: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = LocalSereneColors.current
    val auraSize = 240.dp * scale
    val gap = 16.dp * scale
    // The numerals fill a fixed-size dial, so they are sized in dp: a large font scale would
    // otherwise clip them. They are already far larger than any text the font scale gives.
    val density = LocalDensity.current
    val countdownSize = with(density) { (68.dp * scale).toSp() }
    val countdownSpacing = with(density) { (3.dp * scale).toSp() }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.weight(1f).padding(bottom = gap),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Current Step Pill. Announced when the step changes; the countdown is not, or it
            // would be read out every second.
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

        // Large Glowing Countdown Numbers
        Box(
            modifier = Modifier
                .size(auraSize)
                .clearAndSetSemantics {
                    contentDescription = remainingDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(stepProgress, 0f..1f)
                    if (isPaused) stateDescription = "Paused"
                },
            contentAlignment = Alignment.Center
        ) {
            // Soft Aura
            Box(
                modifier = Modifier
                    .size(auraSize * auraScale)
                    .clip(CircleShape)
                    .background(colors.accentGlow)
            )

            Text(
                text = remaining,
                fontSize = countdownSize,
                fontWeight = FontWeight.Light,
                letterSpacing = countdownSpacing,
                color = colors.onBackground,
                softWrap = false
            )
        }

        Box(
            modifier = Modifier.weight(1f).padding(top = gap),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
    }
}

/** "+30s": gives the current step a little more time. A small pill with a 48dp touch target. */
@Composable
private fun ExtendStepButton(onClick: () -> Unit) {
    val colors = LocalSereneColors.current
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClickLabel = "Add 30 seconds to this step", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+${EXTEND_STEP_SECONDS}s",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface.copy(alpha = 0.85f))
                .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .semantics { contentDescription = "Add $EXTEND_STEP_SECONDS seconds" }
        )
    }
}

private const val EXTEND_STEP_SECONDS = 30

/** The smallest touch target either platform's guidelines allow. */
private val MinTouchTarget = 48.dp

/** Segmented progress rail: one segment per step, filled up to the current one. */
@Composable
private fun StepRail(stepCount: Int, currentIndex: Int) {
    val colors = LocalSereneColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                    .height(5.dp)
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
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = colors.onPrimary,
            modifier = Modifier.size(size * 0.475f)
        )
    }
}

@Composable
private fun CelebrationOverlay(
    routine: Routine,
    onDismiss: () -> Unit
) {
    val colors = LocalSereneColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable { onDismiss() }
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
                fontSize = 44.sp
            )

            Text(
                text = "Routine Complete",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface
            )

            Text(
                text = "You accomplished \"${routine.title}\" (${routine.formattedTotalDuration}). Take a moment to feel centered and refreshed.",
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.primary)
                    .clickable { onDismiss() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Return to Routines",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimary
                )
            }
        }
    }
}
