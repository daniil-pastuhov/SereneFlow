package com.sereneflow.timer.platform

/**
 * The voice assistant that runs the app's commands: Siri through the App Shortcuts in iosApp's
 * RoutineIntents.swift, Google Assistant through the App Actions in androidApp's shortcuts.xml.
 */
enum class VoiceAssistant { SIRI, GOOGLE_ASSISTANT }

expect val voiceAssistant: VoiceAssistant

/**
 * Opens the system app that lists the app's voice commands and can set them up further (iOS:
 * Shortcuts), or null where there is none.
 */
expect val openVoiceCommandsApp: (() -> Unit)?
