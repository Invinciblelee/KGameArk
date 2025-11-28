package com.game.engine.math

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin

operator fun Size.times(origin: TransformOrigin): Offset {
    return Offset(width * origin.pivotFractionX, height * origin.pivotFractionY)
}