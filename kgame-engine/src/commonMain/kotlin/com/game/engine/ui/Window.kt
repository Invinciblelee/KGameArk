package com.game.engine.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun rememberDraggableWindowManager(): DraggableWindowManager {
    return remember { DraggableWindowManager() }
}

@Stable
class DraggableWindow(
    val id: String,
    val title: String,
    val size: DpSize = DpSize(200.dp, 150.dp),
    position: Offset = Offset.Zero,
) {
    private var containerWidthPx: Float = Float.MAX_VALUE
    private var containerHeightPx: Float = Float.MAX_VALUE
    private var windowWidthPx: Float = 0f
    private var windowHeightPx: Float = 0f

    private val animatable by lazy {
        Animatable(
            initialValue = clampPosition(position),
            typeConverter = Offset.VectorConverter
        )
    }

    val position: Offset
        get() = animatable.value

    internal fun setContainerSize(width: Float, height: Float, density: Density) {
        containerWidthPx = width
        containerHeightPx = height
        windowWidthPx = with(density) { size.width.toPx() }
        windowHeightPx = with(density) { size.height.toPx() }
    }

    private fun clampPosition(newPosition: Offset): Offset {
        val clampedX = newPosition.x.coerceIn(0f, containerWidthPx - windowWidthPx)
        val clampedY = newPosition.y.coerceIn(0f, containerHeightPx - windowHeightPx)
        return Offset(clampedX, clampedY)
    }

    suspend fun animateTo(newPosition: Offset) {
        animatable.animateTo(clampPosition(newPosition))
    }

    suspend fun snapTo(newPosition: Offset) {
        animatable.snapTo(clampPosition(newPosition))
    }

    suspend fun panBy(amount: Offset) {
        snapTo(animatable.value + amount)
    }

}

class DraggableWindowManager {
    internal val windows = mutableStateListOf<DraggableWindow>()
    private val pendingRemovalWindows = mutableStateSetOf<String>()

    fun addWindow(window: DraggableWindow) {
        if (windows.none { it.id == window.id }) {
            windows.add(window)
        } else {
            pendingRemovalWindows.remove(window.id)
            bringToFront(window.id)
        }
    }

    fun removeWindow(window: DraggableWindow) {
        pendingRemovalWindows.add(window.id)
    }

    fun removeWindow(id: String) {
        val window = windows.find { it.id == id }
        if (window != null) {
            removeWindow(window)
        }
    }

    internal fun confirmRemoval(id: String) {
        windows.removeAll { it.id == id }
        pendingRemovalWindows.remove(id)
    }

    internal fun isPendingRemoval(id: String): Boolean {
        return pendingRemovalWindows.contains(id)
    }

    fun bringToFront(id: String) {
        val window = windows.find { it.id == id }
        if (window != null) {
            windows.remove(window)
            windows.add(window)
        }
    }
}

/**
 * Used to manage the state of a draggable window.
 * @param windowManager The [DraggableWindowManager] that owns this window.
 * @param shape The shape of the window in the container.
 * @param containerColor The background color of the window.
 * @param contentColor The color of the content of the window.
 * @param tonalElevation The tonal elevation of the window.
 * @param shadowElevation The shadow elevation of the window.
 * @param border The border of the window.
 * @param header The header of the window.
 * @param content The content of the window.
 */
@Composable
fun DraggableWindowGroup(
    windowManager: DraggableWindowManager,
    shape: Shape = MaterialTheme.shapes.small,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 8.dp,
    border: BorderStroke? = null,
    header: @Composable (window: DraggableWindow) -> Unit = { window ->
        WindowHeader(
            title = window.title,
            onClose = { windowManager.removeWindow(window) }
        )
    },
    content: @Composable (window: DraggableWindow) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val coroutineScope = rememberCoroutineScope()

        windowManager.windows.forEachIndexed { index, window ->
            key(window.id) {
                LaunchedEffect(containerWidthPx, containerHeightPx, density) {
                    window.setContainerSize(containerWidthPx, containerHeightPx, density)
                }

                val transitionState = remember { MutableTransitionState(false) }
                val isMarkRemoval = windowManager.isPendingRemoval(window.id)

                LaunchedEffect(isMarkRemoval) {
                    transitionState.targetState = !isMarkRemoval
                }

                LaunchedEffect(transitionState.currentState, transitionState.isIdle) {
                    if (transitionState.isIdle && !transitionState.currentState) {
                        windowManager.confirmRemoval(window.id)
                    }
                }

                AnimatedVisibility(
                    visibleState = transitionState,
                    enter = fadeIn() + scaleIn(initialScale = 0.1f),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .zIndex(index.toFloat())
                        .absoluteOffset {
                            IntOffset(
                                window.position.x.roundToInt(),
                                window.position.y.roundToInt()
                            )
                        }
                        .size(window.size)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                windowManager.bringToFront(window.id)
                            }
                        }
                ) {
                    Surface(
                        shape = shape,
                        color = containerColor,
                        contentColor = contentColor,
                        tonalElevation = tonalElevation,
                        shadowElevation = shadowElevation,
                        border = border,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            coroutineScope.launch {
                                                window.panBy(dragAmount)
                                            }
                                        }
                                    }
                            ) {
                                header(window)
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                content(window)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WindowHeader(
    title: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 12.dp, end = 8.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.titleSmall
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Window",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
