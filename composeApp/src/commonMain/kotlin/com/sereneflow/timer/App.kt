package com.sereneflow.timer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.repository.Repositories
import com.sereneflow.timer.session.TimerSession
import com.sereneflow.timer.session.rememberNotificationPermissionRequest
import com.sereneflow.timer.platform.publishQuickStart
import com.sereneflow.timer.platform.QuickStartItem
import com.sereneflow.timer.platform.quickStartItems
import com.sereneflow.timer.transfer.RoutineTransfer
import com.sereneflow.timer.transfer.shareFile
import com.sereneflow.timer.ui.platform.ConfirmDialog
import com.sereneflow.timer.theme.LocalSereneColors
import com.sereneflow.timer.theme.SereneFlowTheme
import com.sereneflow.timer.ui.ActiveTimerScreen
import com.sereneflow.timer.ui.RoutineEditorScreen
import com.sereneflow.timer.ui.RoutineListScreen
import com.sereneflow.timer.ui.SettingsScreen
import com.sereneflow.timer.ui.SoundPickerScreen
import com.sereneflow.timer.ui.VoicePickerScreen
import com.sereneflow.timer.ui.rememberPredictiveBackTransition
import com.sereneflow.timer.resources.*
import com.sereneflow.timer.text.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

sealed class AppScreen {
    data object List : AppScreen()
    data class Editor(val routine: Routine?) : AppScreen()
    data class Timer(val routine: Routine) : AppScreen()
    data object Settings : AppScreen()
    data object SoundPicker : AppScreen()
    data object VoicePicker : AppScreen()
}

@Composable
fun App(demoLaunch: DemoLaunch? = null) {
    val routineRepository = Repositories.routines
    val preferencesRepository = Repositories.preferences

    val preferences by preferencesRepository.preferences.collectAsState()
    val activeSession by TimerSession.active.collectAsState()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    var showBackgroundPrompt by remember { mutableStateOf(false) }
    // A routine just added at the end of the list, which the list scrolls to when it next shows.
    var revealRoutineId by remember { mutableStateOf<String?>(null) }
    val backStack = remember {
        mutableStateListOf<AppScreen>(AppScreen.List).apply {
            when (demoLaunch) {
                is DemoLaunch.Editor -> routineRepository.getRoutine(demoLaunch.routineId)?.let { add(AppScreen.Editor(it)) }
                DemoLaunch.Settings -> add(AppScreen.Settings)
                else -> {}
            }
        }
    }
    val currentScreen = backStack.last()
    val backupFileName = stringResource(Res.string.file_backup)
    val restTitle = stringResource(Res.string.rest_step_title)

    // The voice applies to every spoken cue, so it is set on the shared controller.
    LaunchedEffect(preferences.voiceId) {
        TimerSession.audioController.setVoice(preferences.voiceId)
    }

    // The timer screen is pushed by the session effect below once the session is running.
    LaunchedEffect(Unit) {
        if (demoLaunch !is DemoLaunch.Timer) return@LaunchedEffect
        val routine = routineRepository.getRoutine(demoLaunch.routineId) ?: return@LaunchedEffect
        TimerSession.start(routine, preferences, backgroundMode = demoLaunch.backgroundMode, restTitle = restTitle)
            .engine.fastForward(demoLaunch.elapsedSeconds * 1000L)
    }

    fun push(screen: AppScreen) {
        backStack.add(screen)
    }

    // Launcher shortcuts, iOS quick actions and the widget offer the last-started routines.
    val routines by routineRepository.routines.collectAsState()
    // Published only when the offer changes, not on every edit to the list.
    var publishedQuickStart by remember { mutableStateOf<List<QuickStartItem>?>(null) }
    LaunchedEffect(routines, preferences.lastRoutineId) {
        val items = quickStartItems(routines, preferences.lastRoutineId)
        if (items == publishedQuickStart) return@LaunchedEffect
        publishedQuickStart = items
        publishQuickStart(items)
    }

    // A routine started from outside the app (shortcut, widget, link). The session effect below
    // then shows its timer. A routine already running is left alone rather than ended.
    val pendingExternal by ExternalActions.pending.collectAsState()
    LaunchedEffect(pendingExternal) {
        val start = pendingExternal as? ExternalAction.StartRoutine ?: return@LaunchedEffect
        ExternalActions.consume(start)
        if (TimerSession.active.value != null) return@LaunchedEffect
        val id = if (start.routineId == ExternalAction.LAST_ROUTINE) {
            preferences.lastRoutineId ?: routines.firstOrNull()?.id
        } else {
            start.routineId
        }
        val routine = id?.let { findRoutine(routines, it) }?.takeIf { it.steps.isNotEmpty() } ?: return@LaunchedEffect
        // Shown over whatever screen is open (an editor keeps its unsaved edits underneath).
        TimerSession.start(routine, preferences, preferences.backgroundMode, restTitle)
        preferencesRepository.setLastRoutine(routine.id)
    }

    fun pop() {
        if (backStack.size <= 1) return
        // Leaving the timer screen ends the session; it never keeps running without its UI.
        if (backStack.last() is AppScreen.Timer) TimerSession.stop()
        backStack.removeAt(backStack.lastIndex)
    }

    // The session outlives the UI (activity recreation, opening from a notification), so show
    // its screen whenever one is running, and close it when the session is ended elsewhere
    // (the notification's End button).
    LaunchedEffect(activeSession) {
        val session = activeSession
        val showingTimer = backStack.last() is AppScreen.Timer
        if (session != null && !showingTimer) backStack.add(AppScreen.Timer(session.routine))
        if (session == null && showingTimer) backStack.removeAt(backStack.lastIndex)
    }

    // System back (Android) and the edge swipe (iOS) pop the stack. Screens with unsaved state
    // register their own handler, which takes precedence while they are composed.
    // While the gesture moves, the screen below is previewed (see rememberPredictiveBackTransition).
    val backNavigationState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = backNavigationState,
        isBackEnabled = backStack.size > 1,
        onBackCompleted = ::pop
    )
    val screenTransition = rememberPredictiveBackTransition(
        current = currentScreen,
        backTarget = backStack.getOrElse(backStack.lastIndex - 1) { currentScreen },
        navigationState = backNavigationState,
        label = "ScreenTransition"
    )

    SereneFlowTheme(themeMode = preferences.themeMode) {
        // Asked once, after the first routine is saved; changeable later in Settings.
        if (showBackgroundPrompt) {
            ConfirmDialog(
                title = stringResource(Res.string.background_prompt_title),
                message = stringResource(Res.string.background_prompt_message),
                confirmLabel = stringResource(Res.string.background_prompt_confirm),
                dismissLabel = stringResource(Res.string.background_prompt_dismiss),
                destructive = false,
                onConfirm = {
                    showBackgroundPrompt = false
                    preferencesRepository.setBackgroundMode(true)
                    requestNotificationPermission()
                },
                onDismiss = {
                    showBackgroundPrompt = false
                    preferencesRepository.setBackgroundMode(false)
                }
            )
        }

        // A routine file opened with the app or picked in Settings: confirm, then add its routines.
        val importAction = pendingExternal as? ExternalAction.ImportRoutines
        if (importAction != null) {
            val offered = remember(importAction) {
                try {
                    RoutineTransfer.decode(importAction.contents)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            if (offered == null) {
                ConfirmDialog(
                    title = stringResource(Res.string.import_failed_title),
                    message = stringResource(Res.string.import_failed_message),
                    confirmLabel = stringResource(Res.string.action_ok),
                    destructive = false,
                    onConfirm = { ExternalActions.consume(importAction) },
                    onDismiss = { ExternalActions.consume(importAction) }
                )
            } else {
                ConfirmDialog(
                    title = if (offered.size == 1) stringResource(Res.string.import_one_title, offered.single().title)
                        else pluralStringResource(Res.plurals.import_many_title, offered.size, offered.size),
                    message = if (offered.size == 1) {
                        stringResource(
                            Res.string.import_one_message,
                            stepCountText(offered.single()),
                            totalDuration(offered.single().totalDurationSeconds)
                        )
                    } else {
                        offered.take(5).joinToString("\n") { it.title } +
                            (if (offered.size > 5) "\n…" else "") +
                            "\n\n" + stringResource(Res.string.import_many_note)
                    },
                    confirmLabel = stringResource(Res.string.action_import),
                    destructive = false,
                    onConfirm = {
                        ExternalActions.consume(importAction)
                        revealRoutineId = routineRepository.import(importAction.contents).firstOrNull()?.id
                    },
                    onDismiss = { ExternalActions.consume(importAction) }
                )
            }
        }

        screenTransition.AnimatedContent(
            // The theme's background under screens that are part-way in or out, rather than the
            // window's launch background.
            modifier = Modifier.fillMaxSize().background(LocalSereneColors.current.background),
            transitionSpec = {
                // Push slides in from the trailing edge; pop slides back out to it. A pop is a
                // screen that has left the back stack, or one a back gesture is moving below.
                val isPop = initialState !in backStack ||
                    backStack.indexOf(targetState) < backStack.indexOf(initialState)
                if (isPop) {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(320)) { -it / 4 }) togetherWith
                            (fadeOut(tween(220)) + slideOutHorizontally(tween(320)) { it })
                } else {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(320)) { it }) togetherWith
                            (fadeOut(tween(220)) + slideOutHorizontally(tween(320)) { -it / 4 })
                }
            }
        ) { screen ->
            when (screen) {
                is AppScreen.List -> {
                    RoutineListScreen(
                        routineRepository = routineRepository,
                        defaultVisualEffect = preferences.defaultVisualEffect,
                        onStartRoutine = { routine ->
                            TimerSession.start(routine, preferences, preferences.backgroundMode, restTitle)
                            preferencesRepository.setLastRoutine(routine.id)
                            push(AppScreen.Timer(routine))
                        },
                        onEditRoutine = { routine -> push(AppScreen.Editor(routine)) },
                        onCreateNewRoutine = { push(AppScreen.Editor(null)) },
                        onOpenSettings = { push(AppScreen.Settings) },
                        onDeleteRoutines = { ids ->
                            // A deleted routine can't keep running (normally the timer covers this screen).
                            if (TimerSession.active.value?.routine?.id in ids) TimerSession.stop()
                            routineRepository.deleteRoutines(ids)
                        },
                        revealRoutineId = revealRoutineId,
                        onRevealed = { revealRoutineId = null }
                    )
                }
                is AppScreen.Editor -> {
                    RoutineEditorScreen(
                        routine = screen.routine,
                        preferences = preferences,
                        onSaveRoutine = { saved ->
                            val isNew = routineRepository.getRoutine(saved.id) == null
                            routineRepository.saveRoutine(saved)
                            if (isNew) revealRoutineId = saved.id
                            pop()
                            if (!preferences.backgroundModeAsked) showBackgroundPrompt = true
                        },
                        onDuplicateRoutine = { toDuplicate ->
                            revealRoutineId = routineRepository.duplicateRoutine(toDuplicate, toDuplicate.title).id
                            pop()
                        },
                        onBack = ::pop
                    )
                }
                is AppScreen.Timer -> {
                    // Captured on entry so the screen can still render during its exit animation
                    // after the session has been stopped.
                    val session = remember(screen) { TimerSession.active.value }
                    if (session != null) {
                        ActiveTimerScreen(
                            session = session,
                            keepScreenOn = preferences.keepScreenOn,
                            onClose = ::pop
                        )
                    }
                }
                is AppScreen.Settings -> {
                    SettingsScreen(
                        preferencesRepository = preferencesRepository,
                        onOpenSoundPicker = { push(AppScreen.SoundPicker) },
                        onOpenVoicePicker = { push(AppScreen.VoicePicker) },
                        routineCount = routines.size,
                        onExportRoutines = {
                            shareFile(
                                RoutineTransfer.fileName(backupFileName),
                                RoutineTransfer.MIME_TYPE,
                                RoutineTransfer.encode(routineRepository.routines.value)
                            )
                        },
                        onBack = ::pop
                    )
                }
                is AppScreen.SoundPicker -> {
                    SoundPickerScreen(preferencesRepository = preferencesRepository, onBack = ::pop)
                }
                is AppScreen.VoicePicker -> {
                    VoicePickerScreen(preferencesRepository = preferencesRepository, onBack = ::pop)
                }
            }
        }
    }
}
