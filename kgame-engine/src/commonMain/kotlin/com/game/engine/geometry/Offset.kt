package com.game.engine.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ScaleFactor
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The angle radians of the Offset
 */
fun Offset.getAngleRadians(): Float {
    return atan2(y, x)
}

/**
 * The angle degrees of the Offset
 */
fun Offset.getAngleDegrees(): Float {
    val angle = getAngleRadians()
    return (angle * 180 / PI).toFloat()
}

/**
 * The normalized Offset
 */
fun Offset.normalized(): Offset {
    return if (getDistanceSquared() > 0f) this / getDistance() else Offset.Zero
}

/**
 * The limited Offset
 */
fun Offset.limit(max: Float): Offset {
    return if (getDistanceSquared() > max * max) normalized() * max else this
}

/**
 * The distance to the target Offset
 */
fun Offset.distanceTo(other: Offset): Float {
    val dx = other.x - this.x
    val dy = other.y - this.y
    return sqrt(dx * dx + dy * dy)
}

/**
 * The square of the distance to the target Offset
 */
fun Offset.distanceToSquared(other: Offset): Float {
    val dx = other.x - this.x
    val dy = other.y - this.y
    return dx * dx + dy * dy
}

/**
 * The angle radians to the target Offset
 */
fun Offset.radiansTo(other: Offset): Float {
    return atan2(other.y - this.y, other.x - this.x)
}

/**
 * The angle degrees to the target Offset
 */
fun Offset.degreesTo(other: Offset): Float {
    val rad = degreesTo(other)
    return (rad * 180 / PI).toFloat()
}

/**
 * The rotated Offset
 */
fun Offset.rotateRadians(rad: Float): Offset {
    val c = cos(rad)
    val s = sin(rad)
    return Offset(x * c - y * s, x * s + y * c)
}

/**
 * The rotated Offset
 */
fun Offset.rotateDegrees(deg: Float): Offset {
    val rad = deg * PI / 180f
    return rotateRadians(rad.toFloat())
}

/**
 * The dot product of the Offset
 */
fun Offset.dot(vec: Offset): Float {
    return x * vec.x + y * vec.y
}

/**
 * The cross product of the Offset
 */
fun Offset.cross(vec: Offset): Float {
    return x * vec.y - y * vec.x
}

/**
 * Scales the Offset by the given factor
 */
fun Offset.scale(factor: ScaleFactor): Offset {
    return Offset(x * factor.scaleX, y * factor.scaleY)
}
