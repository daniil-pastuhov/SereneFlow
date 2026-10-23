package com.sereneflow.timer.platform

actual val voiceAssistant: VoiceAssistant = VoiceAssistant.GOOGLE_ASSISTANT

// Assistant has no screen listing an app's commands.
actual val openVoiceCommandsApp: (() -> Unit)? = null
