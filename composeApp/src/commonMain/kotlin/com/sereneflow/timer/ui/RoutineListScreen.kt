package com.sereneflow.timer.ui

import androidx.compose.ui.semantics.heading
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.stateDescription
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.model.spokenDuration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sereneflow.timer.repository.RoutineRepository
import com.sereneflow.timer.repository.StarterTemplates
import com.sereneflow.timer.transfer.RoutineTransfer
import com.sereneflow.timer.transfer.shareFile
import androidx.compose.material.icons.filled.Share
import com.sereneflow.timer.theme.LocalSereneColors
import com.sereneflow.timer.theme.accentColor
import com.sereneflow.timer.ui.platform.ActionMenu
import com.sereneflow.timer.ui.platform.ConfirmDialog
import com.sereneflow.timer.ui.platform.MenuAction
import com.sereneflow.timer.ui.platform.PlatformIcons

@Composable
fun RoutineListScreen(
    routineRepository: RoutineRepository,
    /** Shown on routines whose visual effect is Default. */
    defaultVisualEffect: VisualEffectType,
    onStartRoutine: (Routine) -> Unit,
    onEditRoutine: (Routine) -> Unit,
    onCreateNewRoutine: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Deletes the routines with these ids (and ends a session running one of them). */
    onDeleteRoutines: (Set<String>) -> Unit,
    /** A routine just added (at the end of the list) to scroll into view, then [onRevealed]. */
    revealRoutineId: String? = null,
    onRevealed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalSereneColors.current
    val routines by routineRepository.routines.collectAsState()
    val gridState = rememberLazyGridState()
    // Added here (template, duplicate) rather than from another screen.
    var addedHere by remember { mutableStateOf<String?>(null) }

    // New routines go to the end of the list, which may be off screen: bring them into view.
    val toReveal = addedHere ?: revealRoutineId
    LaunchedEffect(toReveal, routines) {
        val index = routines.indexOfFirst { it.id == toReveal }
        if (index < 0) return@LaunchedEffect
        gridState.animateScrollToItem(index)
        if (toReveal == addedHere) addedHere = null else onRevealed()
    }

    // Selection mode: entered by long-pressing a card, left when nothing is selected any more.
    var selection by remember { mutableStateOf(emptySet<String>()) }
    // Routines deleted meanwhile (here or elsewhere) drop out of the selection.
    val selectedIds = remember(selection, routines) { selection.filterTo(mutableSetOf()) { id -> routines.any { it.id == id } } }
    val selecting = selectedIds.isNotEmpty()
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selecting,
        onBackCompleted = { selection = emptySet() }
    )

    if (confirmDeleteSelected && selecting) {
        val doomed = routines.filter { it.id in selectedIds }
        ConfirmDialog(
            title = doomed.singleOrNull()?.let { "Delete \"${it.title}\"?" } ?: "Delete ${doomed.size} routines?",
            message = if (doomed.size == 1) "This routine will be removed permanently."
                else "These routines will be removed permanently.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDeleteSelected = false
                onDeleteRoutines(selectedIds)
                selection = emptySet()
            },
            onDismiss = { confirmDeleteSelected = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            // Blank, or one of the starter templates, which is added straight to the list.
            var showNewMenu by remember { mutableStateOf(false) }
            // Out of the way while selecting, as the contextual bar holds that mode's actions.
            AnimatedVisibility(visible = !selecting, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            Box {
                ActionMenu(
                    expanded = showNewMenu,
                    title = "New routine",
                    actions = listOf(MenuAction("Blank routine", Icons.Default.Add, onClick = onCreateNewRoutine)) +
                        StarterTemplates.all.map { template ->
                            MenuAction("From template: ${template.title}") { addedHere = routineRepository.addFromTemplate(template).id }
                        },
                    onDismiss = { showNewMenu = false }
                )
                FloatingActionButton(
                    onClick = { showNewMenu = true },
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp), spotColor = colors.primary)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null) // the text says it
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Routine",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Ambient Top Bar & Header, or the contextual bar while selecting.
            AnimatedContent(
                targetState = selecting,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ListTopBar"
            ) { showSelectionBar ->
                if (showSelectionBar) {
                    SelectionBar(
                        count = selectedIds.size,
                        allSelected = selectedIds.size == routines.size,
                        onClose = { selection = emptySet() },
                        onSelectAll = { selection = routines.mapTo(mutableSetOf()) { it.id } },
                        onShare = {
                            val shared = routines.filter { it.id in selectedIds }
                            shareFile(RoutineTransfer.fileNameFor(shared), RoutineTransfer.MIME_TYPE, RoutineTransfer.encode(shared))
                        },
                        onDelete = { confirmDeleteSelected = true }
                    )
                } else {
                    HeaderSection(onOpenSettings = onOpenSettings)
                }
            }

            // Routine Cards List
            if (routines.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            }

            // One column on phones; two or more where cards would otherwise stretch (iPad, an
            // unfolded iPhone Duo, landscape, tablets).
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.fillMaxSize(),
                state = gridState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(routines, key = { it.id }) { routine ->
                    RoutineCard(
                        routine = routine,
                        visualEffect = routine.visualEffect ?: defaultVisualEffect,
                        onPlay = { onStartRoutine(routine) },
                        onEdit = { onEditRoutine(routine) },
                        onDuplicate = { addedHere = routineRepository.duplicateRoutine(routine.id)?.id },
                        onDelete = { onDeleteRoutines(setOf(routine.id)) },
                        selecting = selecting,
                        selected = routine.id in selectedIds,
                        onToggleSelected = {
                            selection = if (routine.id in selectedIds) selectedIds - routine.id else selectedIds + routine.id
                        },
                        onStartSelecting = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            selection = setOf(routine.id)
                        }
                    )
                }
            }
        }
    }
}

/** The list's top bar while routines are selected: how many, and what can be done with them. */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalSereneColors.current
    Row(
        // As tall as the header it replaces, so the cards don't move.
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 26.dp, bottom = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Cancel selection", tint = colors.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$count selected",
            modifier = Modifier
                .weight(1f)
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            fontSize = 22.sp,
            fontWeight = FontWeight.Normal,
            color = colors.onBackground
        )
        IconButton(onClick = onSelectAll, enabled = !allSelected, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.SelectAll,
                contentDescription = "Select all",
                tint = colors.onSurfaceVariant.copy(alpha = if (allSelected) 0.38f else 1f)
            )
        }
        IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Share, contentDescription = "Share", tint = colors.onSurfaceVariant)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val colors = LocalSereneColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "No routines yet", modifier = Modifier.semantics { heading() }, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = colors.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap New Routine to create your first mindful flow.",
            fontSize = 14.sp,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HeaderSection(
    onOpenSettings: () -> Unit
) {
    val colors = LocalSereneColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "SERENEFLOW",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = colors.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Breathe & Begin",
                modifier = Modifier.semantics { heading() },
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                color = colors.onBackground
            )
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant.copy(alpha = 0.7f))
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    visualEffect: VisualEffectType,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    /** Selection mode: a tap toggles [selected], and the card's own buttons are off. */
    selecting: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onStartSelecting: () -> Unit
) {
    val colors = LocalSereneColors.current
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val accent = routine.accentColor

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete \"${routine.title}\"?",
            message = "This routine will be removed permanently.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Shadow must come before clip/background, otherwise it is drawn inside the card.
            .shadow(2.dp, RoundedCornerShape(24.dp), ambientColor = colors.accentGlow, spotColor = colors.accentGlow)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.cardBackground)
            .then(
                if (accent == null) Modifier else Modifier.drawBehind {
                    // The routine's colour: a soft wash and a band along the leading edge.
                    drawRect(accent.copy(alpha = 0.08f))
                    val band = 6.dp.toPx()
                    val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - band
                    drawRect(accent, topLeft = Offset(x, 0f), size = Size(band, size.height))
                }
            )
            .then(
                if (selected) Modifier.background(colors.primary.copy(alpha = 0.10f))
                    .border(3.dp, colors.primary, RoundedCornerShape(24.dp))
                else Modifier.border(1.dp, accent?.copy(alpha = 0.55f) ?: colors.cardBorder, RoundedCornerShape(24.dp))
            )
            .then(
                if (selecting) {
                    // One stop for a screen reader: the card, its selected state and a tap to toggle.
                    Modifier
                        .selectable(selected = selected, role = Role.Checkbox, onClick = onToggleSelected)
                        .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
                } else {
                    // The card's buttons take their own presses; a long press anywhere else selects.
                    Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onStartSelecting() }) }
                }
            )
            .padding(20.dp)
    ) {
        Column {
            // Header Row: Title + Duration Chip + Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Title and description, read as one.
                // Long press is the touch gesture; screen readers get it as this stop's action.
                Column(
                    modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                        if (!selecting) onLongClick(label = "Select") { onStartSelecting(); true }
                    }
                ) {
                    Text(
                        text = routine.title,
                        modifier = Modifier.semantics { heading() },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface
                    )
                    if (routine.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = routine.description,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                if (selecting) {
                    // Where the options button was: whether the card is selected (the card says it).
                    Box(
                        modifier = Modifier.offset(x = 6.dp, y = (-6).dp).size(48.dp).clearAndSetSemantics {},
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (selected) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else Box {
                    // A 48dp target, nudged into the card's padding so the icon stays where it was.
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.offset(x = 6.dp, y = (-6).dp).size(48.dp)
                    ) {
                        Icon(
                            imageVector = PlatformIcons.More,
                            contentDescription = "Options",
                            tint = colors.onSurfaceVariant
                        )
                    }

                    ActionMenu(
                        expanded = showMenu,
                        title = routine.title,
                        actions = listOf(
                            MenuAction("Edit", Icons.Default.Edit, onClick = onEdit),
                            MenuAction("Duplicate", Icons.Default.ContentCopy, onClick = onDuplicate),
                            MenuAction("Share", Icons.Default.Share) {
                                shareFile(
                                    RoutineTransfer.fileNameFor(routine),
                                    RoutineTransfer.MIME_TYPE,
                                    RoutineTransfer.encode(listOf(routine))
                                )
                            },
                            MenuAction("Delete", Icons.Default.Delete, destructive = true) { confirmDelete = true }
                        ),
                        onDismiss = { showMenu = false }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tags and the first steps, read as one summary.
            Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                // Step tags & Info Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Total duration tag
                    Box(
                        modifier = Modifier
                            .clearAndSetSemantics { contentDescription = "Total ${spokenDuration(routine.totalDurationSeconds)}" }
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.primaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = routine.formattedTotalDuration,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primary
                            )
                        }
                    }

                    // Steps count tag
                    Box(
                        modifier = Modifier
                            .clearAndSetSemantics {
                                contentDescription = if (routine.hasRounds) {
                                    "${routine.steps.size.plural("step")}, ${routine.roundCount} rounds"
                                } else routine.stepCountText
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = routine.stepCountText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurfaceVariant
                        )
                    }

                    // Visual effect badge; the emoji is left out of what a screen reader says.
                    val effectName = when (visualEffect) {
                        VisualEffectType.WATER -> "Water"
                        VisualEffectType.BONSAI_TREE -> "Bonsai"
                        VisualEffectType.COSMIC_ORBIT -> "Orbit"
                        VisualEffectType.BREATHING_LOTUS -> "Lotus"
                        VisualEffectType.HOURGLASS -> "Hourglass"
                    }
                    val visualEmoji = "${visualEffect.emoji} $effectName"
                    Box(
                        modifier = Modifier
                            .clearAndSetSemantics { contentDescription = "$effectName effect" }
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = visualEmoji,
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Step sequence visual preview line
                StepsPreviewBar(steps = routine.steps)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom action row: Click to play
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (selecting) 0.4f else 1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                colors.primary,
                                colors.secondary
                            )
                        )
                    )
                    // Off while selecting; a long press selects, like on the rest of the card.
                    .combinedClickable(
                        enabled = !selecting,
                        role = Role.Button,
                        onLongClick = onStartSelecting,
                        onLongClickLabel = "Select",
                        onClick = onPlay
                    )
                    // Names the routine: every card has a button like this.
                    .semantics { contentDescription = "Start ${routine.title}" }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics {}) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Begin Routine",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun StepsPreviewBar(steps: List<com.sereneflow.timer.model.RoutineStep>) {
    val colors = LocalSereneColors.current
    if (steps.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.take(3).forEachIndexed { index, step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = "${step.title}, ${step.spokenDuration}" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = step.title,
                        fontSize = 12.sp,
                        color = colors.onSurface.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Normal
                    )
                }

                Text(
                    text = step.formattedDuration,
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant
                )
            }
        }

        if (steps.size > 3) {
            Text(
                text = "+ ${(steps.size - 3).plural("more step")}",
                fontSize = 11.sp,
                color = colors.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp)
            )
        }
    }
}

/** "1 step", "3 steps". */
private fun Int.plural(noun: String): String = if (this == 1) "1 $noun" else "$this ${noun}s"
