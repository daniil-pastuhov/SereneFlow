package com.sereneflow.timer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sereneflow.timer.model.AppPreferences
import com.sereneflow.timer.model.AppThemeMode
import com.sereneflow.timer.model.SoundMode
import com.sereneflow.timer.audio.PreviewStep
import com.sereneflow.timer.audio.VoiceOption
import com.sereneflow.timer.audio.playStepCue
import com.sereneflow.timer.platform.AppIconSwitcher
import com.sereneflow.timer.model.VisualEffectType
import com.sereneflow.timer.repository.PreferencesRepository
import com.sereneflow.timer.resources.Res
import com.sereneflow.timer.resources.app_icon_midnight_starlight
import com.sereneflow.timer.resources.app_icon_ocean_mist
import com.sereneflow.timer.resources.app_icon_sunset_clay
import com.sereneflow.timer.resources.app_icon_zen_sage
import com.sereneflow.timer.session.TimerSession
import com.sereneflow.timer.session.rememberNotificationPermissionRequest
import com.sereneflow.timer.theme.LocalSereneColors
import com.sereneflow.timer.ui.platform.PlatformIcons
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesRepository: PreferencesRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSereneColors.current
    val preferences by preferencesRepository.preferences.collectAsState()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    val currentTheme = preferences.themeMode
    val defaultVisual = preferences.defaultVisualEffect
    val defaultSound = preferences.defaultSoundMode

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
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
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            val isSelected = mode == currentTheme

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) colors.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
                                    .clickable {
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
                                        contentDescription = "Selected",
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Audio & Voice Guidance Mode
                item {
                    SectionHeader(title = "DEFAULT SOUND", icon = Icons.Default.GraphicEq)
                    SectionNote("Used when you create a new routine.")
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.cardBackground)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SoundMode.entries.forEach { mode ->
                            val isSelected = mode == defaultSound
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) colors.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
                                    .clickable {
                                        preferencesRepository.setDefaultSoundMode(mode)
                                        // A sample of the sound, as a step start plays it.
                                        TimerSession.audioController.stop()
                                        TimerSession.audioController.playStepCue(mode, PreviewStep, stepIndex = 0)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.displayName,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = mode.description,
                                        fontSize = 12.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Voice, for the sounds that speak; also while a chosen voice is set, as it applies
                // to every routine that speaks and must stay visible to be changed back.
                if (defaultSound == SoundMode.VOICE_AND_CHIME || defaultSound == SoundMode.VOICE_ONLY ||
                    preferences.voiceId != null
                ) {
                    item {
                        VoiceSection(
                            selectedId = preferences.voiceId,
                            onSelect = { id ->
                                preferencesRepository.setVoice(id)
                                // A sample in the new voice, as a step start says it; spoken even
                                // when the default sound itself doesn't speak.
                                val sample = if (defaultSound == SoundMode.VOICE_AND_CHIME) defaultSound else SoundMode.VOICE_ONLY
                                TimerSession.audioController.apply {
                                    setVoice(id)
                                    stop()
                                    playStepCue(sample, PreviewStep, stepIndex = 0)
                                }
                            }
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
                    SectionNote("Used when you create a new routine.")
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.cardBackground)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VisualEffectType.entries.forEach { effect ->
                            val isSelected = effect == defaultVisual
                            val emoji = when (effect) {
                                VisualEffectType.WATER -> "🌊"
                                VisualEffectType.BONSAI_TREE -> "🌳"
                                VisualEffectType.COSMIC_ORBIT -> "✨"
                                VisualEffectType.BREATHING_LOTUS -> "🌸"
                                VisualEffectType.HOURGLASS -> "⏳"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) colors.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
                                    .clickable { preferencesRepository.setDefaultVisualEffect(effect) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(text = emoji, fontSize = 20.sp)
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
                                        contentDescription = "Selected",
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceSection(selectedId: String?, onSelect: (String?) -> Unit) {
    val colors = LocalSereneColors.current
    // The speech engine can take a moment to start on Android, so the list arrives later.
    val voices by produceState<List<VoiceOption>?>(initialValue = null) {
        value = TimerSession.audioController.englishVoices()
    }

    SectionHeader(title = "VOICE", icon = Icons.Default.RecordVoiceOver)
    SectionNote("Speaks the step announcements. Tap a voice to hear it.")
    Spacer(modifier = Modifier.height(10.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val list = voices
        when {
            list == null -> VoiceRow("Loading voices…", null, isSelected = false, onClick = null)
            else -> {
                // A saved voice that was since removed from the device falls back to the default.
                val effectiveId = selectedId?.takeIf { id -> list.any { it.id == id } }
                VoiceRow("Default", "The device's standard English voice", effectiveId == null) { onSelect(null) }
                list.forEach { voice ->
                    VoiceRow(voice.name, voice.detail, voice.id == effectiveId) { onSelect(voice.id) }
                }
                if (list.isEmpty()) {
                    Text(
                        text = "No other English voices are installed. You can add some in the device's text-to-speech settings.",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceRow(name: String, detail: String?, isSelected: Boolean, onClick: (() -> Unit)?) {
    val colors = LocalSereneColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = colors.onSurface
            )
            if (detail != null) {
                Text(text = detail, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
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
