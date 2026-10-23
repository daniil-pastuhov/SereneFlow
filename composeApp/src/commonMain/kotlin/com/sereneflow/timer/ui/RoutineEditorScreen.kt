package com.sereneflow.timer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sereneflow.timer.audio.PreviewStep
import com.sereneflow.timer.audio.playStepCue
import com.sereneflow.timer.model.AppPreferences
import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineStep
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.model.VoiceGuidance
import com.sereneflow.timer.model.newStepId
import com.sereneflow.timer.model.withDuplicatedStep
import com.sereneflow.timer.session.TimerSession
import com.sereneflow.timer.theme.LocalSereneColors
import com.sereneflow.timer.ui.platform.ConfirmDialog
import com.sereneflow.timer.ui.platform.DurationPicker
import com.sereneflow.timer.ui.platform.PlatformIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    routine: Routine?,
    /** For what the Default choices currently mean. */
    preferences: AppPreferences,
    onSaveRoutine: (Routine) -> Unit,
    onDuplicateRoutine: (Routine) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSereneColors.current

    var title by remember { mutableStateOf(routine?.title ?: "New Mindful Routine") }
    var description by remember { mutableStateOf(routine?.description ?: "") }
    // null / DEFAULT: whatever Settings says when the routine starts.
    var selectedVisualEffect by remember { mutableStateOf(routine?.visualEffect) }
    var selectedSound by remember { mutableStateOf(routine?.sound) }
    var selectedVoice by remember { mutableStateOf(routine?.voice ?: VoiceGuidance.DEFAULT) }

    var steps by remember {
        mutableStateOf(
            routine?.steps ?: listOf(
                RoutineStep("s1", "Centering & Stillness", 120),
                RoutineStep("s2", "Deep Breathing", 180),
                RoutineStep("s3", "Gentle Reflection", 120)
            )
        )
    }

    // A new routine's placeholder name and steps are selected on first focus, so typing replaces them.
    val untouchedDefaults = rememberUntouchedDefaults {
        if (routine == null) setOf(TITLE_FIELD) + steps.map { it.id } else emptySet()
    }

    val newRoutineId = remember {
        "routine_${kotlin.random.Random.nextLong().let { if (it < 0) -it else it }}"
    }

    // Edits are applied on top of the original so fields the editor doesn't expose (accent color) survive.
    fun buildRoutine(): Routine = (routine ?: Routine(id = newRoutineId, title = "")).copy(
        title = title.ifBlank { "Untitled Routine" },
        description = description,
        visualEffect = selectedVisualEffect,
        sound = selectedSound,
        voice = selectedVoice,
        steps = steps
    )

    var openPicker by remember { mutableStateOf<EditorPicker?>(null) }

    val initialRoutine = remember { buildRoutine() }
    val hasChanges = buildRoutine() != initialRoutine
    var showDiscardDialog by remember { mutableStateOf(false) }
    val requestBack = { if (hasChanges) showDiscardDialog = true else onBack() }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = hasChanges && openPicker == null,
        onBackCompleted = { showDiscardDialog = true }
    )
    // Back on a picker returns to the editor, edits intact, previewing it during the gesture.
    val pickerBackState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = pickerBackState,
        isBackEnabled = openPicker != null,
        onBackCompleted = { openPicker = null }
    )
    val pickerTransition = rememberPredictiveBackTransition(
        current = openPicker,
        backTarget = null,
        navigationState = pickerBackState,
        label = "editorPicker"
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

    // Keyboard Next walks from the routine name through the step titles; Done on the last one.
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val stepFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun stepFocusRequester(stepId: String) = stepFocusRequesters.getOrPut(stepId) { FocusRequester() }

    fun focusStepTitle(index: Int) {
        val stepId = steps.getOrNull(index)?.id ?: return
        val itemIndex = FIRST_STEP_ITEM + index
        coroutineScope.launch {
            // The LazyColumn only composes visible items, so bring the step in before focusing it.
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == itemIndex }) {
                listState.animateScrollToItem(itemIndex)
            }
            try {
                stepFocusRequester(stepId).requestFocus()
            } catch (_: IllegalStateException) {
                // Not attached (the step was removed meanwhile): leave focus where it is.
            }
        }
    }

    fun finishEditing() {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    val totalDurationSecs = steps.sumOf { it.durationSeconds }
    val totalMins = totalDurationSecs / 60
    val totalSecs = totalDurationSecs % 60
    val formattedTotal = if (totalMins > 0) "${totalMins}m ${totalSecs}s" else "${totalSecs}s"

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,
            contentWindowInsets = WindowInsets.safeDrawing,
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
                    state = listState,
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
                            SelectAllOnFocusField(
                                value = title,
                                onValueChange = {
                                    title = it
                                    untouchedDefaults.edited(TITLE_FIELD)
                                },
                                selectAllOnFocus = TITLE_FIELD in untouchedDefaults,
                                label = { Text("Routine Name") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusStepTitle(0) }),
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

                    // Visual effect and sound: each opens a list of its own over the editor
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(colors.cardBackground)
                                .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                                .padding(vertical = 6.dp)
                        ) {
                            NavigationRow(
                                title = "Visual effect",
                                description = "Default: ${preferences.defaultVisualEffect.displayName}",
                                value = selectedVisualEffect?.displayName ?: "Default",
                                onClick = { openPicker = EditorPicker.VisualEffect }
                            )
                            NavigationRow(
                                title = "Sound",
                                description = "Default: ${preferences.defaultSound.displayName}",
                                value = selectedSound?.displayName ?: "Default",
                                onClick = { openPicker = EditorPicker.Sound }
                            )
                        }
                    }

                    // Spoken step names, on top of any sound
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel("VOICE GUIDANCE")

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                VoiceGuidance.entries.forEach { voice ->
                                    OptionChip(
                                        label = if (voice == VoiceGuidance.DEFAULT) {
                                            "Default · ${if (preferences.defaultVoice) "On" else "Off"}"
                                        } else voice.displayName,
                                        isSelected = voice == selectedVoice,
                                        onClick = {
                                            selectedVoice = voice
                                            val speaks = voice == VoiceGuidance.ON ||
                                                (voice == VoiceGuidance.DEFAULT && preferences.defaultVoice)
                                            if (speaks) {
                                                TimerSession.audioController.apply {
                                                    stop()
                                                    playStepCue(CueSound.NONE, voice = true, PreviewStep, stepIndex = 0)
                                                }
                                            }
                                        }
                                    )
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
                            SectionLabel("TIMELINE STEPS (${steps.size})")
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
                            titleFocusRequester = stepFocusRequester(step.id),
                            selectTitleOnFocus = step.id in untouchedDefaults,
                            onTitleImeAction = {
                                if (index < steps.size - 1) focusStepTitle(index + 1) else finishEditing()
                            },
                            onUpdateStep = { updated ->
                                if (updated.title != step.title) untouchedDefaults.edited(step.id)
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
                            onDuplicate = {
                                val copyId = newStepId()
                                steps = steps.withDuplicatedStep(index, copyId)
                                // A copy of a placeholder is a placeholder too.
                                if (step.id in untouchedDefaults) untouchedDefaults.add(copyId)
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
                                    val newStep = RoutineStep(
                                        id = newStepId(),
                                        title = "New Step ${steps.size + 1}",
                                        durationSeconds = 120
                                    )
                                    steps = steps + newStep
                                    untouchedDefaults.add(newStep.id)
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

        // Shown over the editor rather than pushed as a screen, so the unsaved edits stay.
        pickerTransition.AnimatedContent(
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
            }
        ) { picker ->
            when (picker) {
                EditorPicker.VisualEffect -> ChoiceScreen(
                    title = "Visual effect",
                    note = "The animation behind the countdown. Default follows Settings when the routine starts.",
                    choices = listOf(
                        Choice<VisualEffectType?>(
                            null,
                            "Default",
                            "Now ${preferences.defaultVisualEffect.emoji} ${preferences.defaultVisualEffect.displayName}"
                        )
                    ) + VisualEffectType.entries.map {
                        Choice<VisualEffectType?>(it, "${it.emoji} ${it.displayName}", it.description)
                    },
                    selected = selectedVisualEffect,
                    onSelect = { selectedVisualEffect = it },
                    onBack = { openPicker = null }
                )
                EditorPicker.Sound -> ChoiceScreen(
                    title = "Sound",
                    note = "Plays as each step starts. Default follows Settings when the routine starts. " +
                        "Tap a sound to hear it.",
                    choices = listOf(
                        Choice<CueSound?>(null, "Default", "Now ${preferences.defaultSound.displayName}")
                    ) + CueSound.entries.map { Choice<CueSound?>(it, it.displayName, it.description) },
                    selected = selectedSound,
                    onSelect = { sound ->
                        selectedSound = sound
                        TimerSession.audioController.apply {
                            stop()
                            playSound(sound ?: preferences.defaultSound)
                        }
                    },
                    onBack = { openPicker = null }
                )
                null -> {}
            }
        }
    }
}

/** [UntouchedDefaults] key of the routine name; steps use their ids. */
private const val TITLE_FIELD = "title"

/** LazyColumn index of the first step: after the name, look & sound, voice and timeline header items. */
private const val FIRST_STEP_ITEM = 4

/** The lists the editor opens over itself. */
private enum class EditorPicker { VisualEffect, Sound }

@Composable
private fun StepEditorItem(
    index: Int,
    step: RoutineStep,
    isFirst: Boolean,
    isLast: Boolean,
    titleFocusRequester: FocusRequester,
    /** The title is still the editor's placeholder: select it all on focus. */
    selectTitleOnFocus: Boolean,
    /** Next on the keyboard (Done on the last step). */
    onTitleImeAction: () -> Unit,
    onUpdateStep: (RoutineStep) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalSereneColors.current
    var showDurationPicker by remember { mutableStateOf(false) }

    if (showDurationPicker) {
        DurationPicker(
            initialSeconds = step.durationSeconds,
            onConfirm = { seconds ->
                showDurationPicker = false
                onUpdateStep(step.copy(durationSeconds = seconds))
            },
            onDismiss = { showDurationPicker = false }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline Column (Node + Vertical line)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp).fillMaxHeight()
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
                        .weight(1f)
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
                SelectAllOnFocusField(
                    value = step.title,
                    onValueChange = { onUpdateStep(step.copy(title = it)) },
                    selectAllOnFocus = selectTitleOnFocus,
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = if (isLast) ImeAction.Done else ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { onTitleImeAction() },
                        onDone = { onTitleImeAction() }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.divider
                    )
                )

                // Duration Stepper Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Opens the platform's minutes and seconds wheels.
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { showDurationPicker = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = step.formattedDuration,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary
                        )
                    }

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

                // Reorder, duplicate and delete, on a row of their own so the title keeps its width.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepActionButton(
                        icon = Icons.Default.ArrowUpward,
                        description = "Move step ${index + 1} up",
                        enabled = !isFirst,
                        onClick = onMoveUp
                    )
                    StepActionButton(
                        icon = Icons.Default.ArrowDownward,
                        description = "Move step ${index + 1} down",
                        enabled = !isLast,
                        onClick = onMoveDown
                    )
                    StepActionButton(
                        icon = Icons.Default.ContentCopy,
                        description = "Duplicate step ${index + 1}",
                        onClick = onDuplicate
                    )
                    StepActionButton(
                        icon = Icons.Default.Delete,
                        description = "Delete step ${index + 1}",
                        tint = Color(0xFFE53935),
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

/** A small step-card icon with a full-size (48dp) touch target. */
@Composable
private fun StepActionButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = LocalSereneColors.current.onSurfaceVariant
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) tint else LocalSereneColors.current.divider,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = LocalSereneColors.current.primary
    )
}

@Composable
private fun OptionChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = LocalSereneColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.primary else colors.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
        )
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
