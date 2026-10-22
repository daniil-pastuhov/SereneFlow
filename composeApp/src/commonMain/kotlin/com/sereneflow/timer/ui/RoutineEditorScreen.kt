package com.sereneflow.timer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.model.SoundMode
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.theme.LocalSereneColors
import com.sereneflow.timer.ui.platform.ConfirmDialog
import com.sereneflow.timer.ui.platform.PlatformIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    routine: Routine?,
    defaultVisualEffect: VisualEffectType,
    defaultSoundMode: SoundMode,
    onSaveRoutine: (Routine) -> Unit,
    onDuplicateRoutine: (Routine) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSereneColors.current

    var title by remember { mutableStateOf(routine?.title ?: "New Mindful Routine") }
    var description by remember { mutableStateOf(routine?.description ?: "") }
    var selectedVisualEffect by remember { mutableStateOf(routine?.visualEffect ?: defaultVisualEffect) }
    var selectedSoundMode by remember { mutableStateOf(routine?.soundMode ?: defaultSoundMode) }

    var steps by remember {
        mutableStateOf(
            routine?.steps ?: listOf(
                RoutineStep("s1", "Centering & Stillness", 120),
                RoutineStep("s2", "Deep Breathing", 180),
                RoutineStep("s3", "Gentle Reflection", 120)
            )
        )
    }

    val newRoutineId = remember {
        "routine_${kotlin.random.Random.nextLong().let { if (it < 0) -it else it }}"
    }

    // Edits are applied on top of the original so fields the editor doesn't expose (accent color) survive.
    fun buildRoutine(): Routine = (routine ?: Routine(id = newRoutineId, title = "")).copy(
        title = title.ifBlank { "Untitled Routine" },
        description = description,
        visualEffect = selectedVisualEffect,
        soundMode = selectedSoundMode,
        steps = steps
    )

    val initialRoutine = remember { buildRoutine() }
    val hasChanges = buildRoutine() != initialRoutine
    var showDiscardDialog by remember { mutableStateOf(false) }
    val requestBack = { if (hasChanges) showDiscardDialog = true else onBack() }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = hasChanges,
        onBackCompleted = { showDiscardDialog = true }
    )

    if (showDiscardDialog) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your edits to this routine haven't been saved.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = {
                showDiscardDialog = false
                onBack()
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    val totalDurationSecs = steps.sumOf { it.durationSeconds }
    val totalMins = totalDurationSecs / 60
    val totalSecs = totalDurationSecs % 60
    val formattedTotal = if (totalMins > 0) "${totalMins}m ${totalSecs}s" else "${totalSecs}s"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (routine == null) "Create Routine" else "Edit Routine",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(
                            imageVector = PlatformIcons.Back,
                            contentDescription = "Back",
                            tint = colors.onBackground
                        )
                    }
                },
                actions = {
                    if (routine != null) {
                        IconButton(
                            onClick = { onDuplicateRoutine(buildRoutine()) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Duplicate",
                                tint = colors.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { onSaveRoutine(buildRoutine()) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
                )
            )
        }
    ) { paddingValues ->
        ReadableWidth(modifier = Modifier.fillMaxSize().padding(paddingValues)) { sidePadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Title & Description Inputs
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.cardBackground)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Routine Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.divider
                            )
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Mindful Description (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.divider
                            )
                        )
                    }
                }

                // Visual Effect Selector
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "VISUAL EFFECT (PHYSICS)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = colors.primary
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VisualEffectType.entries.forEach { effect ->
                                val isSelected = effect == selectedVisualEffect
                                val iconEmoji = when (effect) {
                                    VisualEffectType.WATER -> "🌊"
                                    VisualEffectType.BONSAI_TREE -> "🌳"
                                    VisualEffectType.COSMIC_ORBIT -> "✨"
                                    VisualEffectType.BREATHING_LOTUS -> "🌸"
                                    VisualEffectType.HOURGLASS -> "⏳"
                                }

                                Box(
                                    modifier = Modifier
                                        .revealWhenInitiallySelected(isSelected)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) colors.primaryContainer else colors.cardBackground)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) colors.primary else colors.cardBorder,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { selectedVisualEffect = effect }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = iconEmoji, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = effect.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) colors.onPrimaryContainer else colors.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Sound Guidance Mode
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "AUDIO & VOICE GUIDANCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = colors.primary
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SoundMode.entries.forEach { mode ->
                                val isSelected = mode == selectedSoundMode
                                Box(
                                    modifier = Modifier
                                        .revealWhenInitiallySelected(isSelected)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSelected) colors.primary else colors.surfaceVariant.copy(alpha = 0.6f))
                                        .clickable { selectedSoundMode = mode }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Steps Timeline Header & Summary
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TIMELINE STEPS (${steps.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = colors.primary
                        )
                        Text(
                            text = "Total: $formattedTotal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                // Interactive Step Items with Timeline Connectors
                itemsIndexed(steps, key = { _, step -> step.id }) { index, step ->
                    StepEditorItem(
                        index = index,
                        step = step,
                        isFirst = index == 0,
                        isLast = index == steps.size - 1,
                        onUpdateStep = { updated ->
                            val list = steps.toMutableList()
                            list[index] = updated
                            steps = list
                        },
                        onMoveUp = {
                            if (index > 0) {
                                val list = steps.toMutableList()
                                val temp = list[index]
                                list[index] = list[index - 1]
                                list[index - 1] = temp
                                steps = list
                            }
                        },
                        onMoveDown = {
                            if (index < steps.size - 1) {
                                val list = steps.toMutableList()
                                val temp = list[index]
                                list[index] = list[index + 1]
                                list[index + 1] = temp
                                steps = list
                            }
                        },
                        onDelete = {
                            if (steps.size > 1) {
                                steps = steps.filterIndexed { i, _ -> i != index }
                            }
                        }
                    )
                }

                // Add Step Button
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.cardBackground.copy(alpha = 0.6f))
                            .border(
                                width = 1.5.dp,
                                color = colors.primary.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                val randId = kotlin.random.Random.nextLong().let { if (it < 0) -it else it }
                                val newStep = RoutineStep(
                                    id = "step_$randId",
                                    title = "New Step ${steps.size + 1}",
                                    durationSeconds = 120
                                )
                                steps = steps + newStep
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Add Next Step",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun StepEditorItem(
    index: Int,
    step: RoutineStep,
    isFirst: Boolean,
    isLast: Boolean,
    onUpdateStep: (RoutineStep) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalSereneColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline Column (Node + Vertical line)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer)
                    .border(1.5.dp, colors.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(80.dp)
                        .background(colors.primary.copy(alpha = 0.35f))
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Step Card Content
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.cardBackground)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Top row: Step Title + Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = step.title,
                        onValueChange = { onUpdateStep(step.copy(title = it)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.divider
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Row {
                        IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Move Up",
                                tint = if (!isFirst) colors.onSurfaceVariant else colors.divider,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Move Down",
                                tint = if (!isLast) colors.onSurfaceVariant else colors.divider,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Duration Stepper Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Duration: ${step.formattedDuration}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DurationPill(label = "-1m") {
                            val newDuration = (step.durationSeconds - 60).coerceAtLeast(10)
                            onUpdateStep(step.copy(durationSeconds = newDuration))
                        }
                        DurationPill(label = "-15s") {
                            val newDuration = (step.durationSeconds - 15).coerceAtLeast(10)
                            onUpdateStep(step.copy(durationSeconds = newDuration))
                        }
                        DurationPill(label = "+15s") {
                            val newDuration = step.durationSeconds + 15
                            onUpdateStep(step.copy(durationSeconds = newDuration))
                        }
                        DurationPill(label = "+1m") {
                            val newDuration = step.durationSeconds + 60
                            onUpdateStep(step.copy(durationSeconds = newDuration))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationPill(
    label: String,
    onClick: () -> Unit
) {
    val colors = LocalSereneColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.8f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onSurfaceVariant
        )
    }
}

/** Scrolls a chip in a horizontally scrolling row into view if it is selected when first shown. */
@Composable
private fun Modifier.revealWhenInitiallySelected(isSelected: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        if (isSelected) requester.bringIntoView()
    }
    return bringIntoViewRequester(requester)
}
