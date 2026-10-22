package com.sereneflow.timer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sereneflow.timer.audio.PreviewStep
import com.sereneflow.timer.audio.VoiceOption
import com.sereneflow.timer.audio.playStepCue
import com.sereneflow.timer.model.SoundMode
import com.sereneflow.timer.repository.PreferencesRepository
import com.sereneflow.timer.session.TimerSession
import com.sereneflow.timer.theme.LocalSereneColors
import com.sereneflow.timer.ui.platform.PlatformIcons

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
                    Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = colors.onBackground)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = PlatformIcons.Back, contentDescription = "Back", tint = colors.onBackground)
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
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (choices == null) {
                            ChoiceRow(name = "Loading…", detail = null, isSelected = false, onClick = null)
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

/** The sound new routines start with; picking one plays it, as a step start does. */
@Composable
fun SoundPickerScreen(preferencesRepository: PreferencesRepository, onBack: () -> Unit) {
    val preferences by preferencesRepository.preferences.collectAsState()
    ChoiceScreen(
        title = "Default sound",
        note = "Used when you create a new routine; each routine can change it. Tap a sound to hear it.",
        choices = SoundMode.entries.map { Choice(it, it.displayName, it.description) },
        selected = preferences.defaultSoundMode,
        onSelect = { mode ->
            preferencesRepository.setDefaultSoundMode(mode)
            TimerSession.audioController.stop()
            TimerSession.audioController.playStepCue(mode, PreviewStep, stepIndex = 0)
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
    ChoiceScreen(
        title = "Voice",
        note = "Speaks the step announcements in every routine that uses voice. Tap a voice to hear it.",
        choices = list?.let { loaded ->
            listOf(Choice<String?>(null, "Default", "The device's standard English voice")) +
                loaded.map { Choice<String?>(it.id, it.name, it.detail) }
        },
        selected = selectedId,
        onSelect = { id ->
            preferencesRepository.setVoice(id)
            // A sample in the new voice, as a step start says it; spoken even when the default
            // sound itself doesn't speak.
            val defaultSound = preferences.defaultSoundMode
            val sample = if (defaultSound == SoundMode.VOICE_AND_CHIME) defaultSound else SoundMode.VOICE_ONLY
            TimerSession.audioController.apply {
                setVoice(id)
                stop()
                playStepCue(sample, PreviewStep, stepIndex = 0)
            }
        },
        onBack = onBack,
        footnote = if (list != null && list.isEmpty()) {
            "No other English voices are installed. You can add some in the device's text-to-speech settings."
        } else null
    )
}
