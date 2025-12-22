package com.kgame.engine.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize


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


/**
 * If this [IntSize] not zero then this is returned, otherwise [block] is executed and its
 * result is returned.
 */
fun IntSize.takeOrElse(block: () -> IntSize): IntSize {
    return if (this == IntSize.Zero) block() else this
}