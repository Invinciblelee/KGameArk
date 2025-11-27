package com.game.engine.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.game.engine.asset.AssetKey
import com.game.engine.dsl.GameWorld
import com.game.engine.graphics.withViewportTransform
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Represents a scene in the game.
 *
 * @param key The unique key of the scene.
 * @param engine The game engine.
 * @param onProgress A function to be called to update the progress of loading assets.
 * @param onEnter A function to be called when the scene is entered.
 * @param onExit A function to be called when the scene is exited
 * @param onEnable A function to be called when the scene is enabled.
 * @param onDisable A function to be called when the scene is disabled.
 * @param update A function to be called every frame to update the scene.
 * @param render A function to be called to render the scene on game canvas.
 * @param foregroundUI A function to be called to render the foreground UI, which is rendered on top of the game canvas.
 * @param backgroundUI A function to be called to render the background UI, which is rendered behind the game canvas.
 * @param world The game world.
 * @param assets The assets to be loaded.
 */
@Immutable
class GameScene<T : Any>(
    val key: T,
    private val engine: GameEngine,
    private val onProgress: ((Float) -> Unit)?,
    private val onEnter: (() -> Unit)?,
    private val onExit: (() -> Unit)?,
    private val onEnable: (() -> Unit)?,
    private val onDisable: (() -> Unit)?,
    private val update: ((Float) -> Unit)?,
    private val render: (DrawScope.() -> Unit)?,
    private val foregroundUI: (@Composable BoxScope.() -> Unit)?,
    private val backgroundUI: (@Composable BoxScope.() -> Unit)?,
    private val world: GameWorld?,
    private val assets: Set<AssetKey<*>> = emptySet()
) {
    private var isActive by atomic(false)

    private var isLoading by atomic(false)

    private var isPaused by atomic(false)
    private var isMusicPlaying = false

    private fun disable() {
        if (isPaused) return
        isPaused = true

        isMusicPlaying = engine.audio.isMusicPlaying
        engine.audio.pauseMusic()

        onEnable?.invoke()
    }

    private fun enable() {
        if (!isPaused) return
        isPaused = false

        if (isMusicPlaying) {
            engine.audio.resumeMusic()
        }

        onDisable?.invoke()
    }

    private suspend fun load() {
        if (isLoading) return

        if (assets.isEmpty()) {
            onProgress?.invoke(1f)
            return
        }

        isLoading = true
        try {
            onProgress?.invoke(0f)
            val totalAssets = assets.size.toFloat()
            assets.forEachIndexed { index, key ->
                engine.assets.load(key)
                onProgress?.invoke((index + 1) / totalAssets)
            }
            onProgress?.invoke(1f)
        } finally {
            isLoading = false
        }
    }

    private fun enter() {
        isActive = true

        world?.enter()

        onEnter?.invoke()
    }

    private fun exit() {
        isActive = false

        onExit?.invoke()

        world?.exit()

        for (key in assets) {
            engine.assets.unload(key)
        }
    }

    private fun update(deltaTime: Float) {
        if (isActive) {
            world?.update(deltaTime)
        }
        update?.invoke(deltaTime)
    }

    private fun render(drawScope: DrawScope) {
        drawScope.drawContext.size = engine.virtualSize
        drawScope.withViewportTransform(engine.viewportTransform) {
            if (isActive) {
                world?.render(this)
            }
            render?.invoke(this)
        }
    }

    @Composable
    internal fun Content() {
        val viewportTransform = engine.viewportTransform
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

        var frameTrigger by mutableFloatStateOf(0f)
        val coroutineScope = rememberCoroutineScope()

        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(Unit) {
            val observer = LifecycleEventObserver { _, event ->
                when {
                    event == Lifecycle.Event.ON_START -> enable()
                    event == Lifecycle.Event.ON_STOP -> disable()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            coroutineScope.launch {
                load()
                enter()
            }

            engine.onTick { dt ->
                if (isPaused) return@onTick

                update(dt)

                frameTrigger = dt
            }

            engine.onEnableChanged {
                if (it) enable() else disable()
            }

            onDispose {
                exit()
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                backgroundUI?.invoke(this)
            }

            CompositionLocalProvider(LocalDensity provides inverseDensity) {
                GameCanvasRenderer(engine) {
                    println("Scene render")

                    frameTrigger

                    render(this)
                }
            }

            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                foregroundUI?.invoke(this)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GameScene<*>

        return key == other.key
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }

}