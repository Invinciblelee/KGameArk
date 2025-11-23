package com.game.engine.core

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextMeasurer
import com.game.ecs.injectables.CoordinateTransform
import com.game.ecs.injectables.ViewportTransform
import com.game.engine.asset.AssetsManager
import com.game.engine.audio.AudioManager
import com.game.engine.context.PlatformContext
import com.game.engine.input.InputManager

interface GameScope {
    val context: PlatformContext
    val actualSize: Size
    val virtualSize: Size
    val canvasSize: Size
    val viewportTransform: ViewportTransform
    val coordinateTransform: CoordinateTransform
    val textMeasurer: TextMeasurer

    val input: InputManager
    val audio: AudioManager
    val assets: AssetsManager

    val fps: Int

    val transitionProgress: Float

    fun presentScene(id: String, params: Map<String, Any> = emptyMap())

    fun dismissScene(params: Map<String, Any> = emptyMap())
}

fun GameScope.presentScene(id: String, vararg params: Pair<String, Any>) {
    presentScene(id, params.toMap())
}

fun GameScope.dismissScene(vararg params: Pair<String, Any>) {
    dismissScene(params.toMap())
}