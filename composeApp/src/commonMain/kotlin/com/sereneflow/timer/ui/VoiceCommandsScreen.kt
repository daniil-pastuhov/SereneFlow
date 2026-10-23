package com.sereneflow.timer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sereneflow.timer.platform.VoiceAssistant
import com.sereneflow.timer.platform.openVoiceCommandsApp
import com.sereneflow.timer.platform.voiceAssistant
import com.sereneflow.timer.resources.*
import com.sereneflow.timer.theme.LocalSereneColors
import com.sereneflow.timer.ui.platform.PlatformIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A phrase the platform's voice assistant runs, and what it does. */
data class VoiceCommand(val phrase: String, val action: String)

/** The assistant's name: "Siri" or "Google Assistant". */
val VoiceAssistant.nameResource: StringResource
    get() = when (this) {
        VoiceAssistant.SIRI -> Res.string.voice_assistant_siri
        VoiceAssistant.GOOGLE_ASSISTANT -> Res.string.voice_assistant_google
    }

/** The phrase that starts [routineTitle]. */
@Composable
fun startPhrase(routineTitle: String): String = stringResource(
    when (voiceAssistant) {
        VoiceAssistant.SIRI -> Res.string.voice_siri_start
        VoiceAssistant.GOOGLE_ASSISTANT -> Res.string.voice_google_start
    },
    routineTitle
)

/** The phrase that resumes a paused routine. */
@Composable
fun resumePhrase(): String = stringResource(
    when (voiceAssistant) {
        VoiceAssistant.SIRI -> Res.string.voice_siri_resume
        VoiceAssistant.GOOGLE_ASSISTANT -> Res.string.voice_google_resume
    }
)

/** Every command this platform's assistant runs, with [routineTitle] as the example routine. */
@Composable
fun voiceCommands(routineTitle: String): List<VoiceCommand> {
    val start = VoiceCommand(startPhrase(routineTitle), stringResource(Res.string.voice_action_start))
    val resume = VoiceCommand(resumePhrase(), stringResource(Res.string.voice_action_resume))
    return when (voiceAssistant) {
        VoiceAssistant.SIRI -> listOf(
            start,
            VoiceCommand(stringResource(Res.string.voice_siri_start_any), stringResource(Res.string.voice_action_start_any)),
            VoiceCommand(stringResource(Res.string.voice_siri_pause), stringResource(Res.string.voice_action_pause)),
            resume,
            VoiceCommand(stringResource(Res.string.voice_siri_next), stringResource(Res.string.voice_action_next)),
            VoiceCommand(stringResource(Res.string.voice_siri_stop), stringResource(Res.string.voice_action_stop))
        )
        VoiceAssistant.GOOGLE_ASSISTANT -> listOf(
            start,
            VoiceCommand(stringResource(Res.string.voice_google_start_last), stringResource(Res.string.voice_action_start_last)),
            VoiceCommand(stringResource(Res.string.voice_google_pause), stringResource(Res.string.voice_action_pause)),
            resume,
            VoiceCommand(stringResource(Res.string.voice_google_next), stringResource(Res.string.voice_action_next)),
            VoiceCommand(stringResource(Res.string.voice_google_stop), stringResource(Res.string.voice_action_stop))
        )
    }
}

/**
 * What to say to Siri or Google Assistant, opened from Settings. [routineTitle] is the routine
 * the start phrase names (the last one started), or null when there are none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCommandsScreen(routineTitle: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSereneColors.current
    val commands = voiceCommands(routineTitle ?: stringResource(Res.string.voice_example_routine))
    val openApp = openVoiceCommandsApp
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.voice_commands_title),
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
                        text = stringResource(
                            when (voiceAssistant) {
                                VoiceAssistant.SIRI -> Res.string.voice_commands_note_siri
                                VoiceAssistant.GOOGLE_ASSISTANT -> Res.string.voice_commands_note_google
                            }
                        ),
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
                            .padding(vertical = 6.dp)
                    ) {
                        commands.forEach { command ->
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                                Text(text = command.phrase, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                                Text(text = command.action, fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (openApp != null) {
                    item {
                        Text(
                            text = stringResource(Res.string.voice_commands_open_shortcuts),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button, onClick = openApp)
                                .padding(horizontal = 4.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The one-time tip that voice commands exist, with a phrase to say. [onDismiss] null shows no
 * close button (inside the completion card, which has its own).
 */
@Composable
internal fun VoiceTipCard(text: String, onDismiss: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = LocalSereneColors.current
    Row(
        modifier = modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.copy(alpha = 0.92f))
            .border(1.dp, colors.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = if (onDismiss == null) 14.dp else 0.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardVoice,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.voice_tip_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            Text(text = text, fontSize = 12.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant)
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.voice_tip_dismiss),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
