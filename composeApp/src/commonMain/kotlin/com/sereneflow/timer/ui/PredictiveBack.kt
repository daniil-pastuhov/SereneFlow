package com.sereneflow.timer.ui

import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationEventState
import kotlinx.coroutines.flow.collectLatest

/**
 * A transition between screens that follows the back gesture (Android predictive back, the iOS
 * edge swipe) handled with [navigationState]: while the gesture moves, the transition is scrubbed
 * from [current] toward [backTarget] by the gesture's progress, so the screen behind shows. When
 * the gesture completes the handler navigates and the rest plays out from where the finger let go;
 * when it is cancelled the transition animates back. Other changes of [current] animate as usual.
 */
@Composable
fun <T> rememberPredictiveBackTransition(
    current: T,
    backTarget: T,
    navigationState: NavigationEventState<*>,
    label: String
): Transition<T> {
    val seekable = remember { SeekableTransitionState(current) }
    val latestCurrent by rememberUpdatedState(current)
    val latestBackTarget by rememberUpdatedState(backTarget)

    LaunchedEffect(seekable, navigationState) {
        snapshotFlow { Triple(navigationState.transitionState, latestCurrent, latestBackTarget) }
            .collectLatest { (gesture, current, backTarget) ->
                if (gesture is NavigationEventTransitionState.InProgress &&
                    gesture.direction == NavigationEventTransitionState.TRANSITIONING_BACK &&
                    backTarget != current
                ) {
                    seekable.seekTo(gesture.latestEvent.progress, targetState = backTarget)
                } else if (seekable.currentState != current || seekable.targetState != current) {
                    seekable.animateTo(current)
                }
            }
    }
    return rememberTransition(seekable, label)
}
