package com.game.engine.core

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.text.TextMeasurer
import com.game.ecs.injectables.CoordinateTransform
import com.game.ecs.injectables.ViewportTransform
import com.game.engine.asset.AssetsManager
import com.game.engine.asset.DefaultAssetsManager
import com.game.engine.audio.AudioManager
import com.game.engine.audio.DefaultAudioManager
import com.game.engine.context.PlatformContext
import com.game.engine.geometry.DefaultCoordinateTransform
import com.game.engine.geometry.DefaultViewportTransform
import com.game.engine.input.DefaultInputManager
import com.game.engine.input.InputManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Manages the Transform, Input, Audio, Assets and Scene of the game.
 *
 * @param context The platform context.
 * @param viewportTransform The viewport transform.
 * @param coordinateTransform The coordinate transform.
 * @param input The input manager.
 * @param audio The audio manager.
 * @param assets The assets manager.
 * @param textMeasurer The text measurer.
 *
 * @property actualSize The actual size of the game.
 * @property virtualSize The virtual size of the game.
 * @property fps The frames per second of the game.
 */
class GameEngine(
    override val context: PlatformContext,
    override val viewportTransform: ViewportTransform = DefaultViewportTransform(),
    override val coordinateTransform: CoordinateTransform = DefaultCoordinateTransform(viewportTransform),
    override val input: InputManager = DefaultInputManager(viewportTransform),
    override val audio: AudioManager = DefaultAudioManager(context),
    override val assets: AssetsManager = DefaultAssetsManager(),
    override val textMeasurer: TextMeasurer,
) : GameScope {

    override val actualSize: Size
        get() = viewportTransform.actualSize
    override val virtualSize: Size
        get() = viewportTransform.virtualSize

    override val scaledSize: Size
        get() = viewportTransform.scaledSize

    override var fps: Int by mutableIntStateOf(0)
        private set
    private var frameCount = 0
    private var timeAccumulator = 0f

    private var onTick: ((Float) -> Unit)? = null
    private var onEnableChanged: ((Boolean) -> Unit)? = null

    override fun setEnabled(enabled: Boolean) {
        onEnableChanged?.invoke(enabled)
    }

    internal fun onEnableChanged(onEnableChanged: (Boolean) -> Unit) {
        this.onEnableChanged = onEnableChanged
    }

    internal fun actualSizeChanged(size: Size) {
        viewportTransform.applyToSize(actualSize = size)
    }

    internal fun virtualSizeChanged(size: Size) {
        viewportTransform.applyToSize(virtualSize = size)
    }

    internal fun focusChanged(state: FocusState) {
        if (!state.isFocused) {
            input.clear()
        }
    }

    internal fun handleKeyEvent(event: KeyEvent): Boolean {
        return input.onKeyEvent(event)
    }

    internal suspend fun handlePointerEvent(scope: PointerInputScope, canvasOffset: Offset) {
        scope.detectDragGestures(
            onDragStart = { position ->
                input.onPointerUpdate(position, canvasOffset)
                input.onPointerStart()
            },
            onDragEnd = { input.onPointerEnd() },
            onDragCancel = { input.onPointerEnd() },
            onDrag = { change, _ ->
                change.consume()
                input.onPointerUpdate(change.position, canvasOffset)
            }
        )
    }

    internal suspend inline fun scheduleFrameLoop() {
        var lastFrameTime = 0L
        while (currentCoroutineContext().isActive) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTime == 0L) {
                    lastFrameTime = frameTimeNanos
                }

                val dt = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
                lastFrameTime = frameTimeNanos

                tick(dt.coerceAtMost(0.1f))
            }
        }
    }

    private fun tick(deltaTime: Float) {
        onTick?.invoke(deltaTime)

        calculateFps(deltaTime)

        input.endFrame()
    }

    internal fun onTick(onTick: (Float) -> Unit) {
        this.onTick = onTick
    }

    internal fun release() {
        audio.shutdown()
    }

    private fun calculateFps(deltaTime: Float) {
        frameCount++
        timeAccumulator += deltaTime

        if (timeAccumulator >= 1.0f) {
            fps = frameCount
            frameCount = 0
            timeAccumulator -= 1.0f
        }
    }

}