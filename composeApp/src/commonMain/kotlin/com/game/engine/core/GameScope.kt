package com.game.engine.core

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextMeasurer
import com.game.ecs.injectables.CoordinateTransform
import com.game.ecs.injectables.ViewportTransform
import com.game.engine.asset.AssetsManager
import com.game.engine.audio.AudioManager
import com.game.engine.context.PlatformContext
import com.game.engine.input.InputManager

/**
 * Represents a scope for the game.
 */
interface GameScope {
    /**
     * The platform context.
     */
    val context: PlatformContext

    /**
     * The actual size of the game.
     */
    val actualSize: Size

    /**
     * The virtual size of the game.
     */
    val virtualSize: Size

    /**
     * The scaled size of the game.
     */
    val scaledSize: Size

    /**
     * The viewport transform.
     */
    val viewportTransform: ViewportTransform

    /**
     * The coordinate transform.
     */
    val coordinateTransform: CoordinateTransform

    /**
     * The text measurer.
     */
    val textMeasurer: TextMeasurer

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
     * The frames per second of the game.
     */
    val fps: Int

    /**
     * Sets whether the game is enabled or not.
     * If enabled, the game will be updated and rendered. Otherwise, it will be paused.
     * @param enabled If true, the game is enabled. Otherwise, it is disabled.
     */
    fun setEnabled(enabled: Boolean)
}