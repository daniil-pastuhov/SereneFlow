package com.sereneflow.timer.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sereneflow.timer.audio.PreviewStep
import com.sereneflow.timer.audio.VoiceOption
import com.sereneflow.timer.audio.playStepCue
import com.sereneflow.timer.model.CueSound
import com.sereneflow.timer.repository.PreferencesRepository
import com.sereneflow.timer.session.TimerSession
import com.sereneflow.timer.theme.LocalSereneColors
import com.sereneflow.timer.ui.platform.PlatformIcons
import com.sereneflow.timer.resources.*
import org.jetbrains.compose.resources.stringResource

/** One option of a [ChoiceScreen]. */
data class Choice<T>(val value: T, val name: String, val detail: String?)

/**
 * A screen of its own for picking one of a list of options, opened from a row in Settings, so the
 * list can grow without making Settings long. [choices] is null while they load.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceScreen(
    title: String,
    note: String,
    choices: List<Choice<T>>?,
    selected: T,
    onSelect: (T) -> Unit,
    onBack: () -> Unit,
    footnote: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalSereneColors.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        modifier = Modifier.semantics { heading() },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = PlatformIcons.Back, contentDescription = stringResource(Res.string.action_back), tint = colors.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { paddingValues ->
        ReadableWidth(modifier = Modifier.fillMaxSize().padding(paddingValues)) { sidePadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = note,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                item {
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
                        if (choices == null) {
                            ChoiceRow(name = stringResource(Res.string.loading), detail = null, isSelected = false, onClick = null)
                        } else {
                            choices.forEach { choice ->
                                ChoiceRow(choice.name, choice.detail, choice.value == selected) { onSelect(choice.value) }
                            }
                        }
                    }
                }
                if (footnote != null) {
                    item {
                        Text(
                            text = footnote,
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(name: String, detail: String?, isSelected: Boolean, onClick: (() -> Unit)?) {
    val colors = LocalSereneColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
            .then(
                if (onClick != null) {
                    Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
                } else Modifier
            )
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
                contentDescription = null, // the row is announced as a selected radio button
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** A setting whose options are on a [ChoiceScreen] of their own: its name, the current choice and ›. */
@Composable
internal fun NavigationRow(title: String, description: String, value: String, onClick: () -> Unit) {
    val colors = LocalSereneColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
            Text(text = description, fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = value, fontSize = 14.sp, color = colors.primary, maxLines = 1)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** The sound of routines set to Default; picking one plays it, as a step start does. */
@Composable
fun SoundPickerScreen(preferencesRepository: PreferencesRepository, onBack: () -> Unit) {
    val preferences by preferencesRepository.preferences.collectAsState()
    ChoiceScreen(
        title = stringResource(Res.string.settings_default_sound),
        note = stringResource(Res.string.sound_picker_note),
        choices = CueSound.entries.map { Choice(it, it.displayName, it.description) },
        selected = preferences.defaultSound,
        onSelect = { sound ->
            preferencesRepository.setDefaultSound(sound)
            TimerSession.audioController.stop()
            TimerSession.audioController.playSound(sound)
        },
        onBack = onBack
    )
}

/** The voice of every spoken cue; picking one says a sample step in it. */
@Composable
fun VoicePickerScreen(preferencesRepository: PreferencesRepository, onBack: () -> Unit) {
    val preferences by preferencesRepository.preferences.collectAsState()
    // The speech engine can take a moment to start on Android, so the list arrives later.
    val voices by produceState<List<VoiceOption>?>(initialValue = null) {
        value = TimerSession.audioController.englishVoices()
    }
    val list = voices
    // A saved voice that was since removed from the device falls back to the default.
    val selectedId = preferences.voiceId?.takeIf { id -> list?.any { it.id == id } != false }
    val defaultChoice = Choice<String?>(
        null,
        stringResource(Res.string.value_default),
        stringResource(Res.string.voice_picker_default_detail)
    )
    ChoiceScreen(
        title = stringResource(Res.string.settings_voice),
        note = stringResource(Res.string.voice_picker_note),
        choices = list?.let { loaded ->
            listOf(defaultChoice) +
                loaded.map { Choice<String?>(it.id, it.name, it.detail) }
        },
        selected = selectedId,
        onSelect = { id ->
            preferencesRepository.setVoice(id)
            // A sample in the new voice, as a step start says it, even while voice guidance is off.
            TimerSession.audioController.apply {
                setVoice(id)
                stop()
                playStepCue(preferences.defaultSound, voice = true, PreviewStep, stepIndex = 0)
            }
        },
        onBack = onBack,
        footnote = if (list != null && list.isEmpty()) {
            stringResource(Res.string.voice_picker_none_installed)
        } else null
    )
}
