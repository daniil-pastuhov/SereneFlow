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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.zIndex
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
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
import com.sereneflow.timer.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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

    // Manual order: while a card is dragged the grid shows this list, rearranged as it moves; on
    // drop the move is saved, and the list stays until the repository's order has caught up.
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    val shownRoutines = remember(routines, dragOrder) {
        val order = dragOrder ?: return@remember routines
        val byId = routines.associateBy { it.id }
        order.mapNotNull { byId[it] } + routines.filter { it.id !in order }
    }
    val reorder = rememberGridReorderState(
        gridState = gridState,
        onMove = { from, to ->
            val order = (dragOrder ?: routines.map { it.id }).toMutableList()
            if (from in order.indices && to in order.indices) {
                order.add(to, order.removeAt(from))
                dragOrder = order
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        },
        onDrop = { key ->
            val order = dragOrder ?: return@rememberGridReorderState
            routineRepository.moveRoutine(key as String, order.indexOf(key))
        }
    )
    LaunchedEffect(reorder.draggingKey) {
        val order = dragOrder ?: return@LaunchedEffect
        if (reorder.draggingKey != null) return@LaunchedEffect
        // Normally the next emission; if the list changed some other way meanwhile, it wins.
        withTimeoutOrNull(1000) { snapshotFlow { routines.map { it.id } }.first { it == order } }
        dragOrder = null
    }
    fun moveBy(routine: Routine, by: Int) {
        val index = routines.indexOfFirst { it.id == routine.id }
        if (index >= 0) routineRepository.moveRoutine(routine.id, index + by)
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selecting,
        onBackCompleted = { selection = emptySet() }
    )

    if (confirmDeleteSelected && selecting) {
        val doomed = routines.filter { it.id in selectedIds }
        ConfirmDialog(
            title = doomed.singleOrNull()?.let { stringResource(Res.string.list_delete_one_title, it.title) }
                ?: pluralStringResource(Res.plurals.list_delete_many_title, doomed.size, doomed.size),
            message = pluralStringResource(Res.plurals.list_delete_message, doomed.size),
            confirmLabel = stringResource(Res.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDeleteSelected = false
                onDeleteRoutines(selectedIds)
                selection = emptySet()
            },
            onDismiss = { confirmDeleteSelected = false }
        )
    }

    // The New Routine menu: blank, or one of the starter templates, added straight to the list.
    var newMenuExpanded by remember { mutableStateOf(false) }
    // Selecting takes the menu's place (the contextual bar holds that mode's actions).
    LaunchedEffect(selecting) { if (selecting) newMenuExpanded = false }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = newMenuExpanded,
        onBackCompleted = { newMenuExpanded = false }
    )

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        // While the menu is open, a screen reader stays in it (like a dialog).
        modifier = Modifier.fillMaxSize().then(if (newMenuExpanded) Modifier.clearAndSetSemantics {} else Modifier),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
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
                modifier = Modifier.fillMaxSize().onGloballyPositioned { reorder.gridCoordinates = it },
                state = gridState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(shownRoutines, key = { _, it -> it.id }) { index, routine ->
                    val moveUpLabel = stringResource(Res.string.list_move_up)
                    val moveDownLabel = stringResource(Res.string.list_move_down)
                    val dragging = reorder.draggingKey == routine.id
                    val raised = reorder.isLifted(routine.id)
                    RoutineCard(
                        modifier = Modifier
                            // The others slide out of the way; the dragged card follows the finger.
                            .animateItem(placementSpec = if (raised) null else spring(stiffness = Spring.StiffnessMediumLow))
                            .zIndex(if (raised) 1f else 0f)
                            .graphicsLayer {
                                val translation = reorder.translationOf(routine.id)
                                translationX = translation.x
                                translationY = translation.y
                            },
                        lifted = dragging,
                        dragHandle = Modifier.reorderHandle(
                            reorder,
                            routine.id,
                            onPickUp = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                            onDrop = { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) }
                        ),
                        // Reordering without dragging, for screen readers.
                        moveActions = listOfNotNull(
                            if (index > 0) CustomAccessibilityAction(moveUpLabel) { moveBy(routine, -1); true } else null,
                            if (index < shownRoutines.lastIndex) CustomAccessibilityAction(moveDownLabel) { moveBy(routine, 1); true } else null
                        ),
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

        // Dims the list behind the open menu; a tap outside the menu closes it.
        AnimatedVisibility(visible = newMenuExpanded, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = 0.7f))
                    .pointerInput(Unit) { detectTapGestures { newMenuExpanded = false } }
            )
        }

        AnimatedVisibility(
            visible = !selecting,
            modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            NewRoutineMenu(
                expanded = newMenuExpanded,
                onExpandedChange = { newMenuExpanded = it },
                onBlank = onCreateNewRoutine,
                onTemplate = { template -> addedHere = routineRepository.addFromTemplate(template).id }
            )
        }
    }
}

/**
 * The Material 3 FAB menu for making a routine: a blank one (opens the editor) or one of the
 * [StarterTemplates] (added to the list as is). The button turns into a close button while open.
 */
@Composable
private fun NewRoutineMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onBlank: () -> Unit,
    onTemplate: (Routine) -> Unit
) {
    val colors = LocalSereneColors.current
    val newRoutineLabel = stringResource(Res.string.list_new_routine)
    val menuState = stringResource(if (expanded) Res.string.list_menu_open else Res.string.list_menu_closed)
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                    initialColor = colors.primary,
                    finalColor = colors.primary
                ),
                modifier = Modifier.semantics {
                    contentDescription = newRoutineLabel
                    stateDescription = menuState
                    if (expanded) collapse { onExpandedChange(false); true } else expand { onExpandedChange(true); true }
                }
            ) {
                val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add } }
                Icon(
                    imageVector = icon,
                    contentDescription = null, // the button says it
                    modifier = Modifier.animateIcon(
                        checkedProgress = { checkedProgress },
                        color = ToggleFloatingActionButtonDefaults.iconColor(
                            initialColor = colors.onPrimary,
                            finalColor = colors.onPrimary
                        )
                    )
                )
            }
        }
    ) {
        // Top to bottom; the blank routine, the usual choice, sits next to the button.
        StarterTemplates.all.forEach { template ->
            val templateDescription = stringResource(Res.string.list_template_description, template.title)
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    onTemplate(template)
                },
                text = { Text(template.title) },
                icon = { Icon(templateIcon(template), contentDescription = null) },
                modifier = Modifier.semantics { contentDescription = templateDescription },
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer
            )
        }
        FloatingActionButtonMenuItem(
            onClick = {
                onExpandedChange(false)
                onBlank()
            },
            text = { Text(stringResource(Res.string.list_blank_routine)) },
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer
        )
    }
}

/** An icon that hints at what each starter template is for. */
private fun templateIcon(template: Routine): ImageVector = when (template.id) {
    "template_pomodoro" -> Icons.Default.Timer
    "template_box_breathing" -> Icons.Default.Air
    "template_morning_stretch" -> Icons.Default.SelfImprovement
    "template_hiit" -> Icons.Default.FitnessCenter
    "template_wind-down" -> Icons.Default.Bedtime
    else -> Icons.Default.Timer
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
            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.list_cancel_selection), tint = colors.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = pluralStringResource(Res.plurals.list_selected_count, count, count),
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
                contentDescription = stringResource(Res.string.list_select_all),
                tint = colors.onSurfaceVariant.copy(alpha = if (allSelected) 0.38f else 1f)
            )
        }
        IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Share, contentDescription = stringResource(Res.string.action_share), tint = colors.onSurfaceVariant)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete), tint = colors.onSurfaceVariant)
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
        Text(text = stringResource(Res.string.list_empty_title), modifier = Modifier.semantics { heading() }, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = colors.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.list_empty_message),
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
                text = stringResource(Res.string.list_brand),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = colors.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.list_title),
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
                contentDescription = stringResource(Res.string.list_settings),
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
    onStartSelecting: () -> Unit,
    /** Applied to the drag handle shown while selecting. */
    dragHandle: Modifier,
    /** Being dragged: raised above the others. */
    lifted: Boolean,
    moveActions: List<CustomAccessibilityAction>,
    modifier: Modifier = Modifier
) {
    val colors = LocalSereneColors.current
    var showMenu by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (lifted) 14.dp else 2.dp, label = "CardElevation")
    val scale by animateFloatAsState(if (lifted) 1.03f else 1f, label = "CardScale")
    var confirmDelete by remember { mutableStateOf(false) }
    val accent = routine.accentColor
    val selectionState = stringResource(if (selected) Res.string.list_selected else Res.string.list_not_selected)
    val selectLabel = stringResource(Res.string.action_select)
    val startDescription = stringResource(Res.string.list_start_routine_description, routine.title)

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(Res.string.list_delete_one_title, routine.title),
            message = pluralStringResource(Res.plurals.list_delete_message, 1),
            confirmLabel = stringResource(Res.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // Shadow must come before clip/background, otherwise it is drawn inside the card.
            .shadow(elevation, RoundedCornerShape(24.dp), ambientColor = colors.accentGlow, spotColor = colors.accentGlow)
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
                        .semantics {
                            stateDescription = selectionState
                            customActions = moveActions
                        }
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
                        if (!selecting) {
                            onLongClick(label = selectLabel) { onStartSelecting(); true }
                            customActions = moveActions
                        }
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

                if (selecting) Row(modifier = Modifier.offset(x = 6.dp, y = (-6).dp)) {
                    // Drag to reorder (screen readers get Move up / Move down on the card instead).
                    Box(
                        modifier = Modifier.size(48.dp).clearAndSetSemantics {}.then(dragHandle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                    }
                    // Where the options button was: whether the card is selected (the card says it).
                    Box(
                        modifier = Modifier.size(48.dp).clearAndSetSemantics {},
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
                            contentDescription = stringResource(Res.string.list_options),
                            tint = colors.onSurfaceVariant
                        )
                    }

                    ActionMenu(
                        expanded = showMenu,
                        title = routine.title,
                        actions = listOf(
                            MenuAction(stringResource(Res.string.action_edit), Icons.Default.Edit, onClick = onEdit),
                            MenuAction(stringResource(Res.string.action_duplicate), Icons.Default.ContentCopy, onClick = onDuplicate),
                            MenuAction(stringResource(Res.string.action_share), Icons.Default.Share) {
                                shareFile(
                                    RoutineTransfer.fileNameFor(routine),
                                    RoutineTransfer.MIME_TYPE,
                                    RoutineTransfer.encode(listOf(routine))
                                )
                            },
                            MenuAction(stringResource(Res.string.action_delete), Icons.Default.Delete, destructive = true) { confirmDelete = true }
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
                    val totalDescription = stringResource(Res.string.list_total_description, spokenDuration(routine.totalDurationSeconds))
                    Box(
                        modifier = Modifier
                            .clearAndSetSemantics { contentDescription = totalDescription }
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
                    val stepsDescription = if (routine.hasRounds) {
                        stringResource(
                            Res.string.list_steps_and_rounds_description,
                            pluralStringResource(Res.plurals.step_count, routine.steps.size, routine.steps.size),
                            pluralStringResource(Res.plurals.rounds_count, routine.roundCount, routine.roundCount)
                        )
                    } else routine.stepCountText
                    Box(
                        modifier = Modifier
                            .clearAndSetSemantics {
                                contentDescription = stepsDescription
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
                    val effectName = stringResource(
                        when (visualEffect) {
                            VisualEffectType.WATER -> Res.string.effect_water_short
                            VisualEffectType.BONSAI_TREE -> Res.string.effect_bonsai_short
                            VisualEffectType.COSMIC_ORBIT -> Res.string.effect_orbit_short
                            VisualEffectType.BREATHING_LOTUS -> Res.string.effect_lotus_short
                            VisualEffectType.HOURGLASS -> Res.string.effect_hourglass_short
                        }
                    )
                    val effectDescription = stringResource(Res.string.list_effect_description, effectName)
                    val visualEmoji = "${visualEffect.emoji} $effectName"
                    Box(
                        modifier = Modifier
                            .clearAndSetSemantics { contentDescription = effectDescription }
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
                        onLongClickLabel = selectLabel,
                        onClick = onPlay
                    )
                    // Names the routine: every card has a button like this.
                    .semantics { contentDescription = startDescription }
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
                        text = stringResource(Res.string.list_begin_routine),
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
            val stepDescription = stringResource(Res.string.list_step_description, step.title, step.spokenDuration)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = stepDescription },
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
                text = pluralStringResource(Res.plurals.list_more_steps, steps.size - 3, steps.size - 3),
                fontSize = 11.sp,
                color = colors.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp)
            )
        }
    }
}
