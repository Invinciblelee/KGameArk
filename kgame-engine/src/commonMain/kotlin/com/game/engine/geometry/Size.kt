package com.game.engine.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ScaleFactor


/**
 * Converts a fractional [TransformOrigin] into an absolute pixel [Offset]
 * based on the dimensions of this [Size].
 *
 * For example, for a `Size(100f, 200f)`, passing `TransformOrigin(0.5f, 0.5f)`
 * (the center) will return `Offset(50f, 100f)`.
 *
 * @param origin The fractional origin point.
 * @return The calculated absolute offset in pixels.
 */
fun Size.toOffset(origin: TransformOrigin): Offset {
    return Offset(width * origin.pivotFractionX, height * origin.pivotFractionY)
}

/**
 * Scales the Offset by the given factor
 */
fun Size.scale(factor: ScaleFactor): Size {
    return Size(width * factor.scaleX, height * factor.scaleY)
}

/**
 * The average dimension of this [Size].
 */
val Size.avgDimension: Float
    get() = (width + height) / 2f