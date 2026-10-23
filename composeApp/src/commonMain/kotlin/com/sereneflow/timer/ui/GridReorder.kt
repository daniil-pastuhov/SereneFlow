package com.sereneflow.timer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Drag-to-reorder for a [LazyGridState]'s items, with foundation only.
 *
 * A drag starts from a handle inside an item ([start]) and follows the finger ([dragTo]). When the
 * dragged item's centre passes over another item, [onMove] is called with their indices so the
 * caller reorders its list; the dragged item is drawn at [translationOf] so it stays under the
 * finger as the grid lays it out at its new index. Near the grid's top or bottom edge the grid
 * scrolls ([AutoScrollWhileDragging]).
 */
@Stable
class GridReorderState internal constructor(
    private val gridState: LazyGridState,
    private val scope: CoroutineScope,
    private val edgePx: Float,
    private val maxScrollPerFramePx: Float,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: (key: Any) -> Unit
) {
    /** The key of the item being dragged, or null. */
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    /** The grid's layout coordinates, to place the finger in it; set with `onGloballyPositioned`. */
    var gridCoordinates: LayoutCoordinates? = null

    // The dragged item's offset when picked up, and how far the finger has moved since.
    private var initialOffset by mutableStateOf(Offset.Zero)
    private var dragDelta by mutableStateOf(Offset.Zero)
    // Where the finger went down, in the grid's coordinates.
    private var grabPoint = Offset.Zero

    private val draggingItem: LazyGridItemInfo?
        get() = draggingKey?.let { key -> gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } }

    // The item just dropped, settling from where it was let go into its place.
    private var releasedKey by mutableStateOf<Any?>(null)
    private val releaseOffset = Animatable(Offset.Zero, Offset.VectorConverter)
    private var releaseJob: Job? = null

    /** Whether the item with [key] is held or settling after being dropped: drawn above the rest. */
    fun isLifted(key: Any): Boolean = key == draggingKey || key == releasedKey

    /** How far to draw the item with [key] from where the grid places it. */
    fun translationOf(key: Any): Offset {
        if (key == releasedKey) return releaseOffset.value
        if (key != draggingKey) return Offset.Zero
        val item = draggingItem ?: return Offset.Zero
        return initialOffset + dragDelta - item.offset.toOffset()
    }

    /**
     * Picks up the item with [key]; [handle] and [downPosition] (in it) are where the finger is.
     * Returns false when that item isn't laid out.
     */
    fun start(key: Any, handle: LayoutCoordinates, downPosition: Offset): Boolean {
        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
        draggingKey = key
        initialOffset = item.offset.toOffset()
        dragDelta = Offset.Zero
        grabPoint = gridCoordinates?.takeIf { it.isAttached && handle.isAttached }
            ?.localPositionOf(handle, downPosition)
            ?: Offset.Zero
        return true
    }

    /**
     * The finger is at [position] in [handle]. Positions, not deltas: the handle moves with the
     * item it drags (translated, then laid out at new indices), so its own deltas would be skewed.
     */
    fun dragTo(handle: LayoutCoordinates, position: Offset) {
        if (draggingKey == null) return
        val grid = gridCoordinates?.takeIf { it.isAttached && handle.isAttached } ?: return
        dragDelta = grid.localPositionOf(handle, position) - grabPoint
        moveIfOverAnother()
    }

    /** Drops the dragged item where it is. */
    fun end() {
        val key = draggingKey ?: return
        val letGoAt = translationOf(key)
        draggingKey = null
        dragDelta = Offset.Zero
        releaseJob?.cancel()
        releasedKey = key
        releaseJob = scope.launch {
            releaseOffset.snapTo(letGoAt)
            releaseOffset.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Offset.VisibilityThreshold))
            releasedKey = null
        }
        onDrop(key)
    }

    private fun moveIfOverAnother() {
        val item = draggingItem ?: return
        val centre = initialOffset + dragDelta + Offset(item.size.width / 2f, item.size.height / 2f)
        val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { other ->
            other.key != item.key &&
                centre.x >= other.offset.x && centre.x <= other.offset.x + other.size.width &&
                centre.y >= other.offset.y && centre.y <= other.offset.y + other.size.height
        } ?: return
        // Moving the first visible item would otherwise scroll the grid to keep it in place.
        if (item.index == gridState.firstVisibleItemIndex || target.index == gridState.firstVisibleItemIndex) {
            gridState.requestScrollToItem(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
        }
        onMove(item.index, target.index)
    }

    /** Pixels to scroll this frame: towards the edge the finger is near, faster the closer it is. */
    internal fun autoScrollStep(): Float {
        if (draggingItem == null) return 0f
        val fingerY = grabPoint.y + dragDelta.y
        val height = gridState.layoutInfo.viewportSize.height.toFloat()
        return when {
            fingerY < edgePx && dragDelta.y < 0 -> -maxScrollPerFramePx * ((edgePx - fingerY) / edgePx).coerceIn(0f, 1f)
            fingerY > height - edgePx && dragDelta.y > 0 -> maxScrollPerFramePx * ((fingerY - (height - edgePx)) / edgePx).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    /** After an auto-scroll step, the item under the dragged one may have changed. */
    internal fun afterScroll() = moveIfOverAnother()
}

@Composable
fun rememberGridReorderState(
    gridState: LazyGridState,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: (key: Any) -> Unit
): GridReorderState {
    val density = LocalDensity.current
    val latestOnMove by rememberUpdatedState(onMove)
    val latestOnDrop by rememberUpdatedState(onDrop)
    val scope = rememberCoroutineScope()
    val state = remember(gridState, density) {
        with(density) {
            GridReorderState(
                gridState,
                scope,
                edgePx = 72.dp.toPx(),
                maxScrollPerFramePx = 14.dp.toPx(),
                onMove = { from, to -> latestOnMove(from, to) },
                onDrop = { key -> latestOnDrop(key) }
            )
        }
    }
    AutoScrollWhileDragging(state, gridState)
    return state
}

/** Scrolls the grid every frame while the dragged item is held near its top or bottom edge. */
@Composable
private fun AutoScrollWhileDragging(state: GridReorderState, gridState: LazyGridState) {
    val dragging = state.draggingKey != null
    LaunchedEffect(dragging) {
        while (dragging) {
            val step = state.autoScrollStep()
            if (step != 0f && gridState.scrollBy(step) != 0f) state.afterScroll()
            withFrameNanos { }
        }
    }
}

/**
 * Makes this element a drag handle for the item with [key]: dragging it (after touch slop, no long
 * press) moves the item. The handle takes the gesture, so the grid doesn't scroll meanwhile.
 */
fun Modifier.reorderHandle(state: GridReorderState, key: Any, onPickUp: () -> Unit, onDrop: () -> Unit): Modifier =
    composed {
        var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        // A handle that leaves the composition mid-drag (selection mode ended) drops its item.
        DisposableEffect(state, key) {
            onDispose { if (state.draggingKey == key) state.end() }
        }
        this
            .onGloballyPositioned { coordinates = it }
            .pointerInput(state, key) {
                detectDragGestures(
                    onDragStart = { down ->
                        val handle = coordinates ?: return@detectDragGestures
                        if (state.start(key, handle, down)) onPickUp()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        coordinates?.let { state.dragTo(it, change.position) }
                    },
                    onDragEnd = {
                        if (state.draggingKey == key) {
                            state.end()
                            onDrop()
                        }
                    },
                    onDragCancel = {
                        if (state.draggingKey == key) {
                            state.end()
                            onDrop()
                        }
                    }
                )
            }
    }
