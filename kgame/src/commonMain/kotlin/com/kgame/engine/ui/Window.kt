package com.kgame.engine.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kgame.engine.geometry.roundToIntOffset
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

val LocalWindowManager = staticCompositionLocalOf<WindowManager> {
    error("No window manager provide.")
}

@Stable
abstract class Window(
    val id: String,
    val size: DpSize,
) {

    constructor(size: DpSize): this(Uuid.random().toString(), size)

    constructor(width: Dp, height: Dp): this(Uuid.random().toString(), DpSize(width, height))

    private var containerWidthPx: Float = Float.MAX_VALUE
    private var containerHeightPx: Float = Float.MAX_VALUE
    private var windowWidthPx: Float = 0f
    private var windowHeightPx: Float = 0f

    private var isPlaced = false

    internal var windowManager: WindowManager? = null
    private val coroutineScope = MainScope()

    private val animatable by lazy {
        Animatable(
            initialValue = Offset.Zero,
            typeConverter = Offset.VectorConverter
        )
    }

    val position: Offset
        get() = animatable.value

    internal suspend fun sizeChanged(
        width: Float,
        height: Float,
        density: Density
    ) {
        val oldContainerWidth = containerWidthPx
        val oldContainerHeight = containerHeightPx

        containerWidthPx = width
        containerHeightPx = height

        with(density) {
            windowWidthPx = size.width.toPx().coerceAtMost(containerWidthPx)
            windowHeightPx = size.height.toPx().coerceAtMost(containerHeightPx)
        }

        if (!isPlaced) {
            isPlaced = true

            val centerX = (containerWidthPx - windowWidthPx) / 2f
            val centerY = (containerHeightPx - windowHeightPx) / 2f

            snapTo(Offset(centerX, centerY))
        } else {
            if (oldContainerWidth > 0 && oldContainerHeight > 0) {
                val ratioX = position.x / oldContainerWidth
                val ratioY = position.y / oldContainerHeight

                val newX = ratioX * containerWidthPx
                val newY = ratioY * containerHeightPx

                snapTo(Offset(newX, newY))
            }
        }
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

    fun dismiss() {
        windowManager?.removeWindow(this)
    }

    fun Modifier.windowDraggable() = pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            coroutineScope.launch {
                panBy(dragAmount)
            }
        }
    }

    @Composable
    abstract fun Content()

}

@Stable
class WindowManager {
    private val _windows = mutableStateListOf<Window>()
    val windows: List<Window> get() = _windows
    private val pendingRemovalWindows = mutableStateSetOf<String>()

    val size: Int get() = _windows.size

    fun addWindow(window: Window) {
        check(windows.none { it.id == window.id }) {
            "The window-${window.id} already exists."
        }
        window.windowManager = this
        _windows.add(window)
    }

    fun removeWindow(window: Window, immediate: Boolean = false) {
        if (immediate) {
            _windows.remove(window)
        } else {
            pendingRemovalWindows.add(window.id)
        }
    }

    fun removeWindow(id: String, immediate: Boolean = false) {
        val window = _windows.find { it.id == id }
        if (window != null) {
            removeWindow(window, immediate)
        }
    }

    fun removeAllWindows(immediate: Boolean = false) {
        if (immediate) {
            pendingRemovalWindows.clear()
            _windows.clear()
        } else {
            pendingRemovalWindows.addAll(_windows.map { it.id })
        }
    }

    internal fun confirmRemoval(id: String) {
        _windows.removeAll { it.id == id }
        pendingRemovalWindows.remove(id)
    }

    internal fun isPendingRemoval(id: String): Boolean {
        return pendingRemovalWindows.contains(id)
    }

    fun bringToFront(id: String) {
        val window = _windows.find { it.id == id }
        if (window != null) {
            _windows.remove(window)
            _windows.add(window)
        }
    }
}

@Composable
fun Window.WindowHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .windowDraggable()
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 12.dp, end = 8.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = { dismiss() },
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

/**
 * Used to manage the state of a window.
 * @param windowManager The [WindowManager] that owns this window.
 */
@Composable
internal fun WindowGroup(
    windowManager: WindowManager,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        windowManager.windows.forEach { window ->
            key(window.id) {
                WindowContent(window, windowManager)
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.WindowContent(
    window: Window,
    windowManager: WindowManager
) {
    val containerWidthPx = constraints.maxWidth.toFloat()
    val containerHeightPx = constraints.maxHeight.toFloat()
    val density = LocalDensity.current

    LaunchedEffect(containerWidthPx, containerHeightPx, density) {
        window.sizeChanged(containerWidthPx, containerHeightPx, density)
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
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = Modifier
            .offset {
                window.position.roundToIntOffset()
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    windowManager.bringToFront(window.id)
                }
            }
    ) {
        Box(modifier = Modifier.size(window.size)) {
            window.Content()
        }
    }
}