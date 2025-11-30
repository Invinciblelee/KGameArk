package com.game.engine.math

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin

fun Size.positionOf(origin: TransformOrigin): Offset {
    return Offset(width * origin.pivotFractionX, height * origin.pivotFractionY)
}

val Size.avgDimension: Float
    get() = (width + height) / 2f