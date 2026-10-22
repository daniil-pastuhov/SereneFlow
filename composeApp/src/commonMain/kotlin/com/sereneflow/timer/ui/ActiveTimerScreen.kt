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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.session.ActiveSession
import com.sereneflow.timer.ui.platform.KeepScreenOn
import com.sereneflow.timer.engine.nowEpochMs
import com.sereneflow.timer.model.SoundMode
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.physics.BonsaiTreeCanvas
import com.sereneflow.timer.physics.BreathingLotusCanvas
import com.sereneflow.timer.physics.CosmicOrbitCanvas
import com.sereneflow.timer.physics.HourglassCanvas
import com.sereneflow.timer.physics.WaterWaveCanvas
import com.sereneflow.timer.theme.LocalSereneColors
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

    // Breathing pulse for timer aura
    val transition = rememberInfiniteTransition(label = "TimerAura")
    val auraScale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraPulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // 1. Full-screen Physics Simulation Backdrop
        when (routine.visualEffect) {
            VisualEffectType.WATER -> {
                WaterWaveCanvas(
                    progress = { 1f - progress() }, // Drains down gracefully
                    primaryColor = colors.primary,
                    secondaryColor = colors.secondary
                )
            }
            VisualEffectType.BONSAI_TREE -> {
                BonsaiTreeCanvas(
                    progress = progress, // Blooms and grows
                    trunkColor = colors.onBackground.copy(alpha = 0.6f),
                    foliageColor = colors.primary,
                    leafColor = colors.secondary
                )
            }
            VisualEffectType.COSMIC_ORBIT -> {
                CosmicOrbitCanvas(
                    progress = progress,
                    coreColor = colors.primary,
                    accentColor = colors.secondary,
                    trackColor = colors.onBackground.copy(alpha = 0.12f)
                )
            }
            VisualEffectType.BREATHING_LOTUS -> {
                BreathingLotusCanvas(
                    progress = progress,
                    petalColor = colors.primary,
                    goldAccent = colors.secondary,
                    trackColor = colors.onBackground.copy(alpha = 0.12f)
                )
            }
            VisualEffectType.HOURGLASS -> {
                HourglassCanvas(
                    progress = progress,
                    glassColor = colors.surfaceVariant,
                    sandColor = colors.primary,
                    accentColor = colors.secondary
                )
            }
        }

        // 2. Main Foreground Overlay & Controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Close, Routine Title, and Step Progress Rails
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = requestClose,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.surface.copy(alpha = 0.85f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = routine.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onBackground
                        )
                        Text(
                            text = "Step ${state.currentStepIndex + 1} of ${routine.steps.size}",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }

                    if (routine.soundMode != SoundMode.MUTE) {
                        IconButton(
                            onClick = { engine.toggleMute() },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(colors.surface.copy(alpha = 0.85f))
                        ) {
                            Icon(
                                imageVector = if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (state.isMuted) "Unmute" else "Mute",
                                tint = if (state.isMuted) colors.onSurfaceVariant else colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Keeps the title centred when there is no sound to toggle.
                        Spacer(modifier = Modifier.size(42.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Segmented Progress Rail
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    routine.steps.forEachIndexed { idx, _ ->
                        val isFinished = idx < state.currentStepIndex
                        val isCurrent = idx == state.currentStepIndex
                        val segmentColor = when {
                            isFinished -> colors.primary
                            isCurrent -> colors.primary.copy(alpha = 0.85f)
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

            // Center Display: Ambient Card with Step Title, Timer Digits & Next Step Preview
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Current Step Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface.copy(alpha = 0.88f))
                        .border(1.dp, colors.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = state.currentStep?.title ?: "Routine Step",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface
                    )
                }

                // Large Glowing Countdown Numbers
                Box(contentAlignment = Alignment.Center) {
                    // Soft Aura
                    Box(
                        modifier = Modifier
                            .size(240.dp * auraScale)
                            .clip(CircleShape)
                            .background(colors.accentGlow)
                    )

                    Text(
                        text = state.formattedRemaining,
                        fontSize = 68.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 3.sp,
                        color = colors.onBackground
                    )
                }

                // Up Next Step Banner
                val next = state.nextStep
                if (next != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface.copy(alpha = 0.75f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Up next: ${next.title} (${next.formattedDuration})",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            // Bottom Player Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Restart Step Button
                IconButton(
                    onClick = { engine.restartStep() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colors.surface.copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restart Step",
                        tint = colors.onSurface
                    )
                }

                // Previous Step Button
                IconButton(
                    onClick = { engine.previousStep() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colors.surface.copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Previous Step",
                        tint = colors.onSurface
                    )
                }

                // Main Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(elevation = 12.dp, shape = CircleShape, spotColor = colors.primary)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(colors.primary, colors.secondary)
                            )
                        )
                        .clickable { engine.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isRunning && !state.isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isRunning && !state.isPaused) "Pause" else "Play",
                        tint = colors.onPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Skip Next Step Button
                IconButton(
                    onClick = { engine.skipStep() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colors.surface.copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Skip Step",
                        tint = colors.onSurface
                    )
                }
            }
        }

        // 3. Completion Celebration Overlay
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
