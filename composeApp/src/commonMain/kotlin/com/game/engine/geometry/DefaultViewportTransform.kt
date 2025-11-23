package com.game.engine.geometry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import com.game.ecs.injectables.ViewportTransform
import com.game.ecs.injectables.ViewportScaleType

class DefaultViewportTransform: ViewportTransform {
    override var actualSize: Size = Size.Zero
    override var virtualSize: Size = Size.Zero

    override var scaleFactor: Float by mutableFloatStateOf(1f)
    override var offsetX: Float by mutableFloatStateOf(0f)
    override var offsetY: Float by mutableFloatStateOf(0f)

    override var scaleType: ViewportScaleType by mutableStateOf(ViewportScaleType.Fill)
}