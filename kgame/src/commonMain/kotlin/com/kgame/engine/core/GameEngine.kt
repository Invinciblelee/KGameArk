package com.kgame.engine.core

import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.text.TextMeasurer
import com.kgame.engine.asset.AssetsManager
import com.kgame.engine.audio.AudioManager
import com.kgame.engine.audio.DefaultAudioManager
import com.kgame.engine.geometry.DefaultResolutionManager
import com.kgame.engine.geometry.ResolutionManager
import com.kgame.engine.input.DefaultInputManager
import com.kgame.engine.input.InputManager
import com.kgame.engine.utils.internal.Disposable
import com.kgame.platform.PlatformContext
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Manages the Transform, Input, Audio, Assets and Scene of the kgame.
 *
 * @param context The platform context.
 * @param resolution The resolution manager.
 * @param input The input manager.
 * @param audio The audio manager.
 * @param assets The assets manager.
 * @param textMeasurer The text measurer.
 * @param focusRequester The focus requester.
 *
 * @property actualSize The actual size of the kgame.
 * @property virtualSize The virtual size of the kgame.
 * @property scaleSize The scaled size of the kgame.
 * @property isEnabled Whether the kgame is enabled.
 */
@Stable
class GameEngine(
    override val context: PlatformContext,
    override val resolution: ResolutionManager = DefaultResolutionManager(),
    override val input: InputManager = DefaultInputManager(resolution),
    override val audio: AudioManager = DefaultAudioManager(context),
    override val assets: AssetsManager,
    override val textMeasurer: TextMeasurer,
    override val focusRequester: FocusRequester,
) : GameScope {

    override val actualSize: Size
        get() = resolution.actualSize
    override val virtualSize: Size
        get() = resolution.virtualSize

    override val scaleSize: Size
        get() = resolution.scaleSize

    override var isEnabled: Boolean by atomic(true)
        private set

    private var isMusicPlaying = false

    private val onTickCallbacks = mutableListOf<TickCallback>()
    private val onEnableChangedCallbacks = mutableListOf<EnableChangedCallback>()

    private var isFocused = true

    init {
        GameGlobals.setCurrentEngine(this)
    }

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
        resolution.setActualSize(size)
    }

    internal fun virtualSizeChanged(size: Size) {
        resolution.setVirtualSize(size)
    }

    internal fun canvasOffsetChanged(offset: Offset) {
        resolution.setCanvasOffset(offset)
    }

    internal fun focusChanged(state: FocusState) {
        if (!state.isFocused) {
            input.clear()
        }
    }

    internal fun setFocused(focused: Boolean) {
        isFocused = focused
        if (focused) {
            focusRequester.requestFocus()
        } else {
            focusRequester.freeFocus()
        }
    }

    internal fun handleKeyEvent(event: KeyEvent): Boolean {
        return isFocused && input.onKeyEvent(event)
    }

    internal fun handlePointerEvent(event: PointerEvent) {
        input.onPointerEvent(event)
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

                tick(deltaTime.coerceIn(0f, 0.033f))
            }
        }
    }

    private fun tick(deltaTime: Float) {
        if (isEnabled) {
            var index = 0
            while (index < onTickCallbacks.size) {
                val callback = onTickCallbacks[index++]
                callback(deltaTime)
            }
        }

        input.endFrame()
    }

    internal fun onTick(onTick: TickCallback): Disposable {
        onTickCallbacks.add(onTick)
        return Disposable { onTickCallbacks.remove(onTick) }
    }

    internal fun release() {
        audio.shutdown()
        assets.clear()
        input.clear()
        GameGlobals.setCurrentEngine(null)
    }

}