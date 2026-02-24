package com.kgame.engine.core

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.kgame.engine.asset.AssetKey
import com.kgame.engine.asset.AssetsManager
import com.kgame.engine.graphics.drawscope.withViewportTransform
import com.kgame.engine.ui.LocalWindowManager
import com.kgame.engine.ui.WindowGroup
import com.kgame.engine.ui.WindowManager
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.launch

/**
 * Represents a scene in the kgame.
 *
 * @param key The unique key of the scene.
 * @param engine The kgame engine.
 * @param onStart A function to be called when the scene is entered.
 * @param onDestroy A function to be called when the scene is exited
 * @param onEnable A function to be called when the scene is enabled.
 * @param onDisable A function to be called when the scene is disabled.
 * @param update A function to be called every frame to update the scene.
 * @param render A function to be called to render the scene on kgame canvas.
 * @param foregroundUI A function to be called to render the foreground UI, which is rendered on top of the kgame canvas.
 * @param backgroundUI A function to be called to render the background UI, which is rendered behind the kgame canvas.
 * @param world The kgame world.
 */
internal class GameScene<T : Any>(
    val key: T,
    private val engine: GameEngine,
    private val onCreate: (suspend () -> Unit)?,
    private val onStart: (() -> Unit)?,
    private val onDestroy: (() -> Unit)?,
    private val onEnable: (() -> Unit)?,
    private val onDisable: (() -> Unit)?,
    private val update: ((Float) -> Unit)?,
    private val render: (DrawScope.() -> Unit)?,
    private val foregroundUI: (@Composable BoxScope.() -> Unit)?,
    private val backgroundUI: (@Composable BoxScope.() -> Unit)?,
    private val world: GameWorld?
) {

    private var isActive by atomic(false)
    private var isCreated by atomic(false)
    private var redrawSignal by mutableStateOf(false)

    private val windowManager = WindowManager()

    private fun invalidate() {
        redrawSignal = !redrawSignal
    }

    private suspend fun create() {
        isActive = true

        onCreate?.invoke()

        isCreated = true
    }

    private fun start() {
        world?.init()

        onStart?.invoke()
    }

    private fun destroy() {
        isActive = false

        world?.dispose()

        onDestroy?.invoke()
    }

    private fun update(deltaTime: Float) {
        if (isActive && isCreated) {
            world?.update(deltaTime)
        }

        if (isActive) {
            update?.invoke(deltaTime)
        }
    }

    private fun render(drawScope: DrawScope) {
        drawScope.drawContext.size = engine.virtualSize
        drawScope.withViewportTransform(engine.resolution) {
            if (isActive && isCreated) {
                world?.render(this)
            }

            if (isActive) {
                render?.invoke(this)
            }
        }
    }

    @Composable
    fun Content() {
        val viewportTransform = engine.resolution
        val scaleFactor = viewportTransform.scaleFactor

        val scaledDensity = remember(scaleFactor) {
            Density(density = scaleFactor)
        }

        val inverseDensity = remember(scaleFactor) {
            Density(
                density = scaleFactor,
                fontScale = if (scaleFactor == 0f) 0f else 1f / scaleFactor
            )
        }

        val coroutineScope = rememberCoroutineScope()

        DisposableEffect(Unit) {
            coroutineScope.launch {
                create()
                start()
            }

            val disposeTick = engine.onTick { dt ->
                update(dt)
                invalidate()
            }

            val disposeEnableChanged = engine.onEnableChanged {
                if (it) onEnable?.invoke() else onDisable?.invoke()
            }

            onDispose {
                disposeTick()
                disposeEnableChanged()
                destroy()
            }
        }

        CompositionLocalProvider(LocalWindowManager provides windowManager) {
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    backgroundUI?.invoke(this)
                }

                CompositionLocalProvider(LocalDensity provides inverseDensity) {
                    GameCanvasRenderer(engine) {
                        redrawSignal

                        render(this)
                    }
                }

                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    foregroundUI?.invoke(this)

                    if (windowManager.size > 0) {
                        WindowGroup(windowManager, modifier = Modifier.matchParentSize())

                        DisposableEffect(Unit) {
                            engine.setFocused(false)
                            onDispose {
                                engine.setFocused(true)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GameCanvasRenderer(
        engine: GameEngine,
        onDraw: DrawScope.() -> Unit
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onGloballyPositioned {
                    engine.canvasOffsetChanged(it.positionInRoot())
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event: PointerEvent = awaitPointerEvent()
                            engine.handlePointerEvent(event)
                        }
                    }
                }
        ) {
            onDraw()
        }
    }

}