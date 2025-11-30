package com.game.engine.math

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

fun ClosedFloatingPointRange<Float>.randomOffset(): Offset {
    return Offset(
        Random.nextFloat() * start,
        Random.nextFloat() * endInclusive
    )
}

fun ClosedFloatingPointRange<Float>.random(): Float {
    return Random.nextFloat() * (endInclusive - start) + start
}

fun ClosedFloatingPointRange<Double>.random(): Double {
    return Random.nextDouble() * (endInclusive - start) + start
}

fun Offset.randomCircleOffset(radius: Float): Offset {
    val angle = Random.nextFloat() * 2 * PI
    val r = sqrt(Random.nextFloat()) * radius
    return this + Offset(
        (r * cos(angle)).toFloat(),
        (r * sin(angle)).toFloat()
    )
}

fun Offset.randomRectangleOffset(halfWidth: Float, halfHeight: Float): Offset {
    val dx = (Random.nextFloat() * 2 - 1) * halfWidth
    val dy = (Random.nextFloat() * 2 - 1) * halfHeight
    return this + Offset(dx, dy)
}

fun Rect.randomOffset(): Offset {
    val x = Random.nextFloat() * width  + left
    val y = Random.nextFloat() * height + top
    return Offset(x, y)
}

fun Color.Companion.random(): Color {
    return Color(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
}