package com.kgame.engine.core

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextMeasurer
import com.kgame.engine.asset.AssetsManager
import com.kgame.engine.audio.AudioManager
import com.kgame.engine.geometry.ResolutionManager
import com.kgame.engine.input.InputManager
import com.kgame.platform.PlatformContext

/**
 * Represents a scope for the kgame.
 */
interface GameScope {
    /**
     * The platform context.
     */
    val context: PlatformContext

    /**
     * The actual size of the kgame.
     */
    val actualSize: Size

    /**
     * The virtual size of the kgame.
     */
    val virtualSize: Size

    /**
     * The scaled size of the kgame.
     */
    val scaleSize: Size

    /**
     * The resolution manager.
     */
    val resolution: ResolutionManager

    /**
     * The input manager.
     */
    val input: InputManager

    /**
     * The audio manager.
     */
    val audio: AudioManager

    /**
     * The assets manager.
     */
    val assets: AssetsManager

    /**
     * The text measurer.
     */
    val textMeasurer: TextMeasurer

    /**
     * The focus requester
     */
    val focusRequester: FocusRequester

    /**
     * Sets whether the kgame is enabled or not.
     * If enabled, the kgame will be updated and rendered. Otherwise, it will be paused.
     */
    val isEnabled: Boolean

    /**
     * Enables the kgame, updating and rendering it.
     * If the kgame is already enabled, this method has no effect.
     */
    fun enable()

    /**
     * Disables the kgame, pausing it.
     * If the kgame is already disabled, this method has no effect.
     */
    fun disable()

}