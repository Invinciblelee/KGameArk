package com.kgame.engine.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The angle of this [Offset] in degrees, in the range from -180 to 180.
 */
fun Offset.angleDegrees(): Float {
    return (angleRadians() * 180 / PI).toFloat()
}

/**
 * The angle of this [Offset] in radians, in the range from -PI to PI.
 * This is calculated using `atan2(y, x)`.
 */
fun Offset.angleRadians(): Float {
    return atan2(y, x)
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
    val rad = radiansTo(other)
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


/**
 * Coverts to [IntOffset]
 */
fun Offset.toIntOffset(): IntOffset {
    return IntOffset(x.toInt(), y.toInt())
}

/**
 * Coverts to [IntOffset], rounds [Offset.x] and [Offset.y] to the nearest integer.
 */
fun Offset.roundToIntOffset(): IntOffset {
    return IntOffset(x.roundToInt(), y.roundToInt())
}

/**
 * If this [IntOffset] not zero then this is returned, otherwise [block] is executed and its
 * result is returned.
 */
fun IntOffset.takeOrElse(block: () -> IntOffset): IntOffset {
    return if (this == IntOffset.Zero) block() else this
}

/**
 * Generates a Rect centered at the current Offset.
 */
fun Offset.expandFromCenter(width: Float, height: Float): Rect {
    val halfW = width / 2f
    val halfH = height / 2f
    return Rect(x - halfW, y - halfH, x + halfW, y + halfH)
}

/**
 * Generates a square Rect centered at the current Offset.
 */
fun Offset.expandFromCenter(size: Float): Rect = expandFromCenter(size, size)

/**
 * Generates a Rect with the current Offset as the bottom-center anchor.
 * Ideal for ground-based effects like tornadoes or cracks.
 */
fun Offset.expandFromBottom(width: Float, height: Float): Rect {
    return Rect(x - width / 2f, y - height, x + width / 2f, y)
}

/**
 * Generates a square Rect with the current Offset as the bottom-center anchor.
 */
fun Offset.expandFromBottom(size: Float): Rect = expandFromBottom(size, size)

/**
 * Generates a Rect with the current Offset as the top-center anchor.
 * Ideal for ceiling or falling effects like rain or lightning.
 */
fun Offset.expandFromTop(width: Float, height: Float): Rect {
    return Rect(x - width / 2f, y, x + width / 2f, y + height)
}

/**
 * Generates a square Rect with the current Offset as the top-center anchor.
 */
fun Offset.expandFromTop(size: Float): Rect = expandFromTop(size, size)

/**
 * Generates a Rect with the current Offset as the middle-left anchor.
 */
fun Offset.expandFromLeft(width: Float, height: Float): Rect {
    return Rect(x, y - height / 2f, x + width, y + height / 2f)
}

/**
 * Generates a square Rect with the current Offset as the middle-left anchor.
 */
fun Offset.expandFromLeft(size: Float): Rect = expandFromLeft(size, size)

/**
 * Generates a Rect with the current Offset as the middle-right anchor.
 */
fun Offset.expandFromRight(width: Float, height: Float): Rect {
    return Rect(x - width, y - height / 2f, x, y + height / 2f)
}

/**
 * Generates a square Rect with the current Offset as the middle-right anchor.
 */
fun Offset.expandFromRight(size: Float): Rect = expandFromRight(size, size)

/**
 * Converts this [Offset] to a [Velocity].
 */
fun Offset.toVelocity(): Velocity = Velocity(x, y)