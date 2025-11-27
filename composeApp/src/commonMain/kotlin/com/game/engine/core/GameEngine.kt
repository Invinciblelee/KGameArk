package com.game.engine.core

import androidx.compose.foundation.gestures.detectDragGestures
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
import com.game.engine.utils.Disposable
import kotlinx.atomicfu.atomic
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

    override var isEnabled: Boolean by atomic(true)
        private set

    private var isMusicPlaying = false

    private val onTickCallbacks = mutableListOf<(Float) -> Unit>()
    private val onEnableChangedCallbacks = mutableListOf<(Boolean) -> Unit>()


    override fun enable() {
        if (isEnabled) return
        isEnabled = true

        if (isMusicPlaying) {
            audio.resumeMusic()
        }

        onEnableChangedCallbacks.forEach { it(true) }
    }

    override fun disable() {
        if (!isEnabled) return
        isEnabled = false

        isMusicPlaying = audio.isMusicPlaying
        audio.pauseMusic()

        onEnableChangedCallbacks.forEach { it(false) }
    }

    internal fun onEnableChanged(onEnableChanged: (Boolean) -> Unit): Disposable {
        onEnableChangedCallbacks.add(onEnableChanged)
        return Disposable { onEnableChangedCallbacks.remove(onEnableChanged) }
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
        if (isEnabled) {
            onTickCallbacks.forEach { it(deltaTime) }
        }

        input.endFrame()
    }

    internal fun onTick(onTick: (Float) -> Unit): Disposable {
        onTickCallbacks.add(onTick)
        return Disposable {  }
    }

    internal fun release() {
        audio.shutdown()
        assets.clear()
        input.clear()
    }

}