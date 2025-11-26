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
     * The canvas size of the game.
     */
    val canvasSize: Size

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
     * The transition progress of the game.
     *
     * If the [GameEngine.currentScene] is loading resources, this value will be between 0 and 1.
     *
     * Usually used to display transition UI.
     *
     * @see GameEngine.transitionProgress
     */
    val transitionProgress: Float

    /**
     * Presents a scene.
     *
     * @param id The id of the scene to present.
     * @param params The parameters to pass to the scene.
     */
    fun presentScene(id: String, params: Map<String, Any> = emptyMap())

    /**
     * Dismisses the current scene.
     *
     * @param params The parameters to pass to the scene.
     */
    fun dismissScene(params: Map<String, Any> = emptyMap())
}

fun GameScope.presentScene(id: String, vararg params: Pair<String, Any>) {
    presentScene(id, params.toMap())
}

fun GameScope.dismissScene(vararg params: Pair<String, Any>) {
    dismissScene(params.toMap())
}