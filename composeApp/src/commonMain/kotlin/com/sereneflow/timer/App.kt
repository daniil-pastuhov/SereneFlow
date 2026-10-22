package com.sereneflow.timer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.sereneflow.timer.repository.PreferencesRepository
import com.sereneflow.timer.repository.RoutineRepository
import com.sereneflow.timer.session.TimerSession
import com.sereneflow.timer.session.rememberNotificationPermissionRequest
import com.sereneflow.timer.ui.platform.ConfirmDialog
import com.sereneflow.timer.storage.createFileStorage
import com.sereneflow.timer.theme.SereneFlowTheme
import com.sereneflow.timer.ui.ActiveTimerScreen
import com.sereneflow.timer.ui.RoutineEditorScreen
import com.sereneflow.timer.ui.RoutineListScreen
import com.sereneflow.timer.ui.SettingsScreen

sealed class AppScreen {
    data object List : AppScreen()
    data class Editor(val routine: Routine?) : AppScreen()
    data class Timer(val routine: Routine) : AppScreen()
    data object Settings : AppScreen()
}

@Composable
fun App(demoLaunch: DemoLaunch? = null) {
    val storage = remember { createFileStorage() }
    val routineRepository = remember { RoutineRepository(storage) }
    val preferencesRepository = remember { PreferencesRepository(storage) }

    val preferences by preferencesRepository.preferences.collectAsState()
    val activeSession by TimerSession.active.collectAsState()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    var showBackgroundPrompt by remember { mutableStateOf(false) }
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

    // The timer screen is pushed by the session effect below once the session is running.
    LaunchedEffect(Unit) {
        if (demoLaunch !is DemoLaunch.Timer) return@LaunchedEffect
        val routine = routineRepository.getRoutine(demoLaunch.routineId) ?: return@LaunchedEffect
        TimerSession.start(routine, preferences.reminderThresholds, backgroundMode = false)
            .engine.fastForward(demoLaunch.elapsedSeconds * 1000L)
    }

    fun push(screen: AppScreen) {
        backStack.add(screen)
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
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = backStack.size > 1,
        onBackCompleted = ::pop
    )

    SereneFlowTheme(themeMode = preferences.themeMode) {
        // Asked once, after the first routine is saved; changeable later in Settings.
        if (showBackgroundPrompt) {
            ConfirmDialog(
                title = "Follow your routine with the screen off?",
                message = "SereneFlow can keep guiding you when the screen is off or you switch apps, " +
                    "with a notification showing the current step and time left. " +
                    "You can change this anytime in Settings.",
                confirmLabel = "Yes, keep me posted",
                dismissLabel = "Not now",
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

        AnimatedContent(
            targetState = currentScreen,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                // Push slides in from the trailing edge; pop slides back out to it.
                val isPop = initialState != AppScreen.List && targetState == AppScreen.List
                if (isPop) {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(320)) { -it / 4 }) togetherWith
                            (fadeOut(tween(220)) + slideOutHorizontally(tween(320)) { it })
                } else {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(320)) { it }) togetherWith
                            (fadeOut(tween(220)) + slideOutHorizontally(tween(320)) { -it / 4 })
                }
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                is AppScreen.List -> {
                    RoutineListScreen(
                        routineRepository = routineRepository,
                        onStartRoutine = { routine ->
                            TimerSession.start(routine, preferences.reminderThresholds, preferences.backgroundMode)
                            push(AppScreen.Timer(routine))
                        },
                        onEditRoutine = { routine -> push(AppScreen.Editor(routine)) },
                        onCreateNewRoutine = { push(AppScreen.Editor(null)) },
                        onOpenSettings = { push(AppScreen.Settings) }
                    )
                }
                is AppScreen.Editor -> {
                    RoutineEditorScreen(
                        routine = screen.routine,
                        defaultVisualEffect = preferences.defaultVisualEffect,
                        defaultSoundMode = preferences.defaultSoundMode,
                        onSaveRoutine = { saved ->
                            routineRepository.saveRoutine(saved)
                            pop()
                            if (!preferences.backgroundModeAsked) showBackgroundPrompt = true
                        },
                        onDuplicateRoutine = { toDuplicate ->
                            routineRepository.duplicateRoutine(toDuplicate)
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
                        onBack = ::pop
                    )
                }
            }
        }
    }
}
