package com.game.engine.core

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.text.TextMeasurer
import com.game.engine.asset.AssetsManager
import com.game.engine.audio.AudioManager
import com.game.engine.audio.DefaultAudioManager
import com.game.engine.geometry.DefaultResolutionManager
import com.game.engine.geometry.ResolutionManager
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
 * @param resolution The viewport transform.
 * @param coordinateTransform The coordinate transform.
 * @param input The input manager.
 * @param audio The audio manager.
 * @param assets The assets manager.
 * @param textMeasurer The text measurer.
 *
 * @property actualSize The actual size of the game.
 * @property virtualSize The virtual size of the game.
 * @property scaledSize The scaled size of the game.
 * @property isEnabled Whether the game is enabled.
 */
@Stable
class GameEngine(
    override val context: PlatformContext,
    override val resolution: ResolutionManager = DefaultResolutionManager(),
    override val input: InputManager = DefaultInputManager(resolution),
    override val audio: AudioManager = DefaultAudioManager(context),
    override val assets: AssetsManager,
    override val textMeasurer: TextMeasurer,
) : GameScope {

    override val actualSize: Size
        get() = resolution.actualSize
    override val virtualSize: Size
        get() = resolution.virtualSize

    override val scaledSize: Size
        get() = resolution.scaledSize

    override var isEnabled: Boolean by atomic(true)
        private set

    private var isMusicPlaying = false

    private val onTickCallbacks = mutableListOf<TickCallback>()
    private val onEnableChangedCallbacks = mutableListOf<EnableChangedCallback>()

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

    internal fun onEnableChanged(onEnableChanged: EnableChangedCallback): Disposable {
        onEnableChangedCallbacks.add(onEnableChanged)
        return Disposable { onEnableChangedCallbacks.remove(onEnableChanged) }
    }

    internal fun actualSizeChanged(size: Size) {
        resolution.applySize(actualSize = size)
    }

    internal fun virtualSizeChanged(size: Size) {
        resolution.applySize(virtualSize = size)
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

    internal suspend fun startTicking() {
        var lastFrameTime = 0L
        while (currentCoroutineContext().isActive) {
            withFrameMillis { frameTimeMillis ->
                if (lastFrameTime == 0L) {
                    lastFrameTime = frameTimeMillis
                }

                val deltaTime = (frameTimeMillis - lastFrameTime) / 1000f
                lastFrameTime = frameTimeMillis

                tick(deltaTime.coerceAtMost(0.1f))
            }
        }
    }

    private fun tick(deltaTime: Float) {
        if (isEnabled) {
            onTickCallbacks.forEach { it(deltaTime) }
        }

        input.endFrame()
    }

    internal fun onTick(onTick: TickCallback): Disposable {
        onTickCallbacks.add(onTick)
        return Disposable {  }
    }

    internal fun release() {
        audio.shutdown()
        assets.clear()
        input.clear()
    }

}