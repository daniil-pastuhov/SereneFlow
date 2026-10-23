package com.sereneflow.timer.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.filled.KeyboardVoice
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
import com.sereneflow.timer.platform.voiceAssistant
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.repository.PreferencesRepository
import com.sereneflow.timer.resources.*
import com.sereneflow.timer.text.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
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
    onOpenVoiceCommands: () -> Unit,
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
    val defaultLabel = stringResource(Res.string.value_default)
    val voiceName = when {
        preferences.voiceId == null -> defaultLabel
        voices == null -> "…" // still loading; don't flash "Default" for a chosen voice
        else -> voices?.firstOrNull { it.id == preferences.voiceId }?.name ?: defaultLabel
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
                        text = stringResource(Res.string.settings_title),
                        modifier = Modifier.semantics { heading() },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = PlatformIcons.Back,
                            contentDescription = stringResource(Res.string.action_back),
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
                    SectionHeader(title = stringResource(Res.string.settings_themes), icon = Icons.Default.Palette)
                    if (AppIconSwitcher.isSupported) {
                        SectionNote(
                            listOfNotNull(
                                stringResource(Res.string.settings_themes_note),
                                AppIconSwitcher.changeNote?.let { stringResource(it) }
                            ).joinToString(" ")
                        )
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
                                        text = mode.displayName(),
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
                    SectionHeader(title = stringResource(Res.string.settings_sound_voice), icon = Icons.Default.GraphicEq)
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
                            title = stringResource(Res.string.settings_default_sound),
                            description = stringResource(Res.string.settings_default_sound_description),
                            value = defaultSound.displayName(),
                            onClick = onOpenSoundPicker
                        )
                        SwitchRow(
                            title = stringResource(Res.string.settings_voice_guidance),
                            description = stringResource(Res.string.settings_voice_guidance_description),
                            checked = preferences.defaultVoice,
                            onCheckedChange = preferencesRepository::setDefaultVoice
                        )
                        // While the voice speaks; also while a chosen voice is set, as it applies
                        // to every routine that speaks and must stay visible to be changed back.
                        if (preferences.defaultVoice || preferences.voiceId != null) {
                            NavigationRow(
                                title = stringResource(Res.string.settings_voice),
                                description = stringResource(Res.string.settings_voice_description),
                                value = voiceName,
                                onClick = onOpenVoicePicker
                            )
                        }
                        SwitchRow(
                            title = stringResource(Res.string.settings_vibration),
                            description = stringResource(Res.string.settings_vibration_description),
                            checked = preferences.hapticsEnabled,
                            onCheckedChange = preferencesRepository::setHapticsEnabled
                        )
                    }
                }

                // Screen & background behaviour
                item {
                    SectionHeader(title = stringResource(Res.string.settings_screen_background), icon = Icons.Default.PhoneAndroid)
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
                            title = stringResource(Res.string.settings_keep_screen_on),
                            description = stringResource(Res.string.settings_keep_screen_on_description),
                            checked = preferences.keepScreenOn,
                            onCheckedChange = preferencesRepository::setKeepScreenOn
                        )
                        SwitchRow(
                            title = stringResource(Res.string.settings_background),
                            description = stringResource(Res.string.settings_background_description),
                            checked = preferences.backgroundMode,
                            onCheckedChange = { enabled ->
                                preferencesRepository.setBackgroundMode(enabled)
                                if (enabled) requestNotificationPermission()
                            }
                        )
                    }
                }

                // Siri or Google Assistant: what to say, on a screen of its own
                item {
                    SectionHeader(title = stringResource(Res.string.settings_voice_commands), icon = Icons.Default.KeyboardVoice)
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
                            title = stringResource(voiceAssistant.nameResource),
                            description = stringResource(Res.string.settings_voice_commands_description),
                            value = "",
                            onClick = onOpenVoiceCommands
                        )
                    }
                }

                // "% left" reminders
                item {
                    SectionHeader(title = stringResource(Res.string.settings_reminders), icon = Icons.Default.NotificationsActive)
                    SectionNote(stringResource(Res.string.settings_reminders_note))
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
                                        text = stringResource(Res.string.settings_reminder_percent, percent),
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
                    SectionHeader(title = stringResource(Res.string.settings_default_effect), icon = Icons.Default.Visibility)
                    SectionNote(stringResource(Res.string.settings_default_effect_note))
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
                                            text = effect.displayName(),
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = colors.onSurface
                                        )
                                        Text(
                                            text = effect.description(),
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
                    SectionHeader(title = stringResource(Res.string.settings_backup), icon = Icons.Default.Backup)
                    SectionNote(stringResource(Res.string.settings_backup_note))
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
                                title = stringResource(Res.string.settings_export),
                                description = stringResource(Res.string.settings_export_description),
                                value = pluralStringResource(Res.plurals.settings_routine_count, routineCount, routineCount),
                                onClick = onExportRoutines
                            )
                        }
                        NavigationRow(
                            title = stringResource(Res.string.settings_import),
                            description = stringResource(Res.string.settings_import_description),
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
    // One switch to screen readers, read with its title and description; the Switch only draws.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
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
            onCheckedChange = null,
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
            modifier = Modifier.semantics { heading() },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = colors.primary
        )
    }
}
