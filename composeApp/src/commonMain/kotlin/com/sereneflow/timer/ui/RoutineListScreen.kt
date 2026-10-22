package com.sereneflow.timer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.repository.RoutineRepository
import com.sereneflow.timer.theme.LocalSereneColors
import com.sereneflow.timer.ui.platform.ActionMenu
import com.sereneflow.timer.ui.platform.ConfirmDialog
import com.sereneflow.timer.ui.platform.MenuAction
import com.sereneflow.timer.ui.platform.PlatformIcons

@Composable
fun RoutineListScreen(
    routineRepository: RoutineRepository,
    onStartRoutine: (Routine) -> Unit,
    onEditRoutine: (Routine) -> Unit,
    onCreateNewRoutine: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSereneColors.current
    val routines by routineRepository.routines.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNewRoutine,
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
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Routine")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "New Routine",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Ambient Top Bar & Header
            HeaderSection(onOpenSettings = onOpenSettings)

            // Routine Cards List
            if (routines.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            }

            // One column on phones; two or more where cards would otherwise stretch (iPad, an
            // unfolded iPhone Duo, landscape, tablets).
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(routines, key = { it.id }) { routine ->
                    RoutineCard(
                        routine = routine,
                        onPlay = { onStartRoutine(routine) },
                        onEdit = { onEditRoutine(routine) },
                        onDuplicate = { routineRepository.duplicateRoutine(routine.id) },
                        onDelete = { routineRepository.deleteRoutine(routine.id) }
                    )
                }
            }
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
        Text(text = "No routines yet", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = colors.onBackground)
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
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                color = colors.onBackground
            )
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(46.dp)
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
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalSereneColors.current
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

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
            .border(1.dp, colors.cardBorder, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column {
            // Header Row: Title + Duration Chip + Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routine.title,
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

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
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
                            MenuAction("Delete", Icons.Default.Delete, destructive = true) { confirmDelete = true }
                        ),
                        onDismiss = { showMenu = false }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Step tags & Info Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Total duration tag
                Box(
                    modifier = Modifier
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

                // Visual effect badge
                val visualEmoji = when (routine.visualEffect) {
                    VisualEffectType.WATER -> "🌊 Water"
                    VisualEffectType.BONSAI_TREE -> "🌳 Bonsai"
                    VisualEffectType.COSMIC_ORBIT -> "✨ Orbit"
                    VisualEffectType.BREATHING_LOTUS -> "🌸 Lotus"
                    VisualEffectType.HOURGLASS -> "⏳ Hourglass"
                }
                Box(
                    modifier = Modifier
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

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom action row: Click to play
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                colors.primary,
                                colors.secondary
                            )
                        )
                    )
                    .clickable { onPlay() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Routine",
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
                modifier = Modifier.fillMaxWidth(),
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
                text = "+ ${steps.size - 3} more steps",
                fontSize = 11.sp,
                color = colors.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp)
            )
        }
    }
}
