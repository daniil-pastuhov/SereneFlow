package com.sereneflow.timer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sereneflow.timer.model.AppPreferences
import com.sereneflow.timer.model.AppThemeMode
import com.sereneflow.timer.audio.VoiceOption
import com.sereneflow.timer.platform.AppIconSwitcher
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.repository.PreferencesRepository
import com.sereneflow.timer.resources.Res
import com.sereneflow.timer.resources.app_icon_midnight_starlight
import com.sereneflow.timer.resources.app_icon_ocean_mist
import com.sereneflow.timer.resources.app_icon_sunset_clay
import com.sereneflow.timer.resources.app_icon_zen_sage
import com.sereneflow.timer.session.TimerSession
import com.sereneflow.timer.ExternalAction
import com.sereneflow.timer.ExternalActions
import com.sereneflow.timer.transfer.rememberRoutineFilePicker
import androidx.compose.material.icons.filled.Backup
import com.sereneflow.timer.session.rememberNotificationPermissionRequest
import com.sereneflow.timer.theme.LocalSereneColors
import com.sereneflow.timer.ui.platform.PlatformIcons
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesRepository: PreferencesRepository,
    onOpenSoundPicker: () -> Unit,
    onOpenVoicePicker: () -> Unit,
    /** How many routines a backup would hold. */
    routineCount: Int,
    onExportRoutines: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSereneColors.current
    // The picked file is confirmed and imported by App, like a file opened from another app.
    val pickRoutineFile = rememberRoutineFilePicker { contents ->
        ExternalActions.post(ExternalAction.ImportRoutines(contents))
    }
    val preferences by preferencesRepository.preferences.collectAsState()
    // The chosen voice's name; "Default" too for one that was since removed from the device.
    val voices by produceState<List<VoiceOption>?>(initialValue = null) {
        value = TimerSession.audioController.englishVoices()
    }
    val voiceName = when {
        preferences.voiceId == null -> "Default"
        voices == null -> "…" // still loading; don't flash "Default" for a chosen voice
        else -> voices?.firstOrNull { it.id == preferences.voiceId }?.name ?: "Default"
    }
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    val currentTheme = preferences.themeMode
    val defaultVisual = preferences.defaultVisualEffect
    val defaultSound = preferences.defaultSound

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Preferences",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = PlatformIcons.Back,
                            contentDescription = "Back",
                            tint = colors.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { paddingValues ->
        ReadableWidth(modifier = Modifier.fillMaxSize().padding(paddingValues)) { sidePadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Theme Section
                item {
                    SectionHeader(title = "HARMONIZED THEMES", icon = Icons.Default.Palette)
                    if (AppIconSwitcher.isSupported) {
                        SectionNote(listOfNotNull("The app icon changes with the theme.", AppIconSwitcher.changeNote).joinToString(" "))
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.cardBackground)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                            .padding(14.dp)
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            val isSelected = mode == currentTheme

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) colors.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
                                    .selectable(selected = isSelected, role = Role.RadioButton) {
                                        preferencesRepository.setThemeMode(mode)
                                        // The app icon follows the theme. Only on a real change: iOS
                                        // shows its alert even when the icon stays the same.
                                        if (AppIconSwitcher.isSupported && AppIconSwitcher.current() != mode) {
                                            AppIconSwitcher.set(mode) {}
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // The theme's app icon, which picking it also switches to.
                                    Image(
                                        painter = painterResource(mode.previewIcon),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Text(
                                        text = mode.displayName,
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = colors.onSurface
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null, // the row is announced as a selected radio button
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Sound and voice: each opens a list of its own, which can grow without making
                // this screen long.
                item {
                    SectionHeader(title = "SOUND & VOICE", icon = Icons.Default.GraphicEq)
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.cardBackground)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                            .padding(vertical = 6.dp)
                    ) {
                        NavigationRow(
                            title = "Default sound",
                            description = "For routines set to Default",
                            value = defaultSound.displayName,
                            onClick = onOpenSoundPicker
                        )
                        SwitchRow(
                            title = "Voice guidance",
                            description = "Speak each step's name and length in routines set to Default.",
                            checked = preferences.defaultVoice,
                            onCheckedChange = preferencesRepository::setDefaultVoice
                        )
                        // While the voice speaks; also while a chosen voice is set, as it applies
                        // to every routine that speaks and must stay visible to be changed back.
                        if (preferences.defaultVoice || preferences.voiceId != null) {
                            NavigationRow(
                                title = "Voice",
                                description = "For spoken step announcements",
                                value = voiceName,
                                onClick = onOpenVoicePicker
                            )
                        }
                        SwitchRow(
                            title = "Vibration",
                            description = "A tap as each step starts, on the 3-2-1 countdown and at the end, " +
                                "even when the routine is muted.",
                            checked = preferences.hapticsEnabled,
                            onCheckedChange = preferencesRepository::setHapticsEnabled
                        )
                    }
                }

                // Screen & background behaviour
                item {
                    SectionHeader(title = "SCREEN & BACKGROUND", icon = Icons.Default.PhoneAndroid)
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.cardBackground)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                            .padding(vertical = 6.dp)
                    ) {
                        SwitchRow(
                            title = "Keep screen on",
                            description = "The display stays awake while a routine is on screen.",
                            checked = preferences.keepScreenOn,
                            onCheckedChange = preferencesRepository::setKeepScreenOn
                        )
                        SwitchRow(
                            title = "Follow routine with screen off",
                            description = "Keep guiding you in the background, with a notification " +
                                "showing the current step and time left.",
                            checked = preferences.backgroundMode,
                            onCheckedChange = { enabled ->
                                preferencesRepository.setBackgroundMode(enabled)
                                if (enabled) requestNotificationPermission()
                            }
                        )
                    }
                }

                // "% left" reminders
                item {
                    SectionHeader(title = "REMINDERS", icon = Icons.Default.NotificationsActive)
                    SectionNote("Announce when this much of each step is left. Steps under 10 seconds are skipped.")
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.cardBackground)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppPreferences.ReminderOptions.forEach { percent ->
                            val selected = percent in preferences.reminderThresholds
                            FilterChip(
                                selected = selected,
                                onClick = { preferencesRepository.toggleReminderThreshold(percent) },
                                label = {
                                    Text(
                                        text = "$percent%",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.primaryContainer,
                                    selectedLabelColor = colors.onSurface,
                                    labelColor = colors.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                // Default Visual Simulation
                item {
                    SectionHeader(title = "DEFAULT VISUAL EFFECT", icon = Icons.Default.Visibility)
                    SectionNote("Used by routines whose visual effect is Default.")
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.cardBackground)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                            .padding(14.dp)
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VisualEffectType.entries.forEach { effect ->
                            val isSelected = effect == defaultVisual

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) colors.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
                                    .selectable(selected = isSelected, role = Role.RadioButton) {
                                        preferencesRepository.setDefaultVisualEffect(effect)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(text = effect.emoji, fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics {})
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = effect.displayName,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = colors.onSurface
                                        )
                                        Text(
                                            text = effect.description,
                                            fontSize = 11.sp,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null, // the row is announced as a selected radio button
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Backup: every routine to one .sereneflow file, and back
                item {
                    SectionHeader(title = "BACKUP", icon = Icons.Default.Backup)
                    SectionNote("Save your routines to a file, or add routines from one. Importing never replaces a routine.")
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.cardBackground)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                            .padding(vertical = 6.dp)
                    ) {
                        if (routineCount > 0) {
                            NavigationRow(
                                title = "Export all routines",
                                description = "Share or save a backup file",
                                value = if (routineCount == 1) "1 routine" else "$routineCount routines",
                                onClick = onExportRoutines
                            )
                        }
                        NavigationRow(
                            title = "Import routines",
                            description = "From a .sereneflow file",
                            value = "",
                            onClick = pickRoutineFile
                        )
                    }
                }
            }
        }
    }
}

private val AppThemeMode.previewIcon: DrawableResource
    get() = when (this) {
        AppThemeMode.ZEN_SAGE -> Res.drawable.app_icon_zen_sage
        AppThemeMode.OCEAN_MIST -> Res.drawable.app_icon_ocean_mist
        AppThemeMode.SUNSET_CLAY -> Res.drawable.app_icon_sunset_clay
        AppThemeMode.MIDNIGHT_STARLIGHT -> Res.drawable.app_icon_midnight_starlight
    }

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalSereneColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
            Text(text = description, fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary
            )
        )
    }
}

@Composable
private fun SectionNote(text: String) {
    val colors = LocalSereneColors.current
    Text(
        text = text,
        fontSize = 12.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 2.dp)
    )
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val colors = LocalSereneColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = colors.primary
        )
    }
}
