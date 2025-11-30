package com.game.engine.math

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Calculates the angle from this Offset to the given target Offset in degrees.
 *
 * @param target The target Offset to calculate the angle to.
 * @return The angle in degrees, ranging from -180 to 180.
 */
fun Offset.angleTo(target: Offset): Float {
    val rad = atan2(target.y - this.y, target.x - this.x)
    return (rad * 180 / PI).toFloat()
}

/**
 * Calculates the angle of this Offset relative to the origin (0,0) in degrees.
 *
 * @return The angle in degrees, ranging from -180 to 180.
 */
fun Offset.angle(): Float {
    val rad = atan2(y, x)
    return (rad * 180f / PI).toFloat()
}

/**
 * Calculates the squared distance between this Offset and another Offset.
 *
 * @param other The other Offset to calculate the distance to.
 * @return The squared distance between the two Offsets.
 */
fun Offset.distSq(other: Offset): Float {
    val dx = this.x - other.x
    val dy = this.y - other.y
    return dx * dx + dy * dy
}

/**
 * Normalizes this Offset to a unit vector.
 *
 * @return A new Offset with the same direction but a length of 1, or Offset.Zero if the original length is 0.
 */
fun Offset.normalize(): Offset {
    val len = getDistance()
    return if (len == 0f) Offset.Zero else this / len
}

/**
 * Rotates this Offset by the given degrees around the given pivot.
 */
fun Offset.rotate(degrees: Float, pivot: Offset): Offset {
    if (degrees == 0f) return this
    val angleRad = degrees * (PI.toFloat() / 180f)
    val sin = sin(angleRad)
    val cos = cos(angleRad)
    val p = this - pivot
    val newX = p.x * cos - p.y * sin
    val newY = p.x * sin + p.y * cos
    return Offset(newX, newY) + pivot
}

/**
 * Scales this Offset by the given factors around the given pivot.
 */
fun Offset.scale(scaleX: Float, scaleY: Float, pivot: Offset): Offset {
    if (scaleX == 1f && scaleY == 1f) return this
    val p = this - pivot
    val newX = p.x * scaleX
    val newY = p.y * scaleY
    return Offset(newX, newY) + pivot
}

/**
 * Translates this Offset by the given offsets.
 */
fun Offset.dot(other: Offset): Float {
    return this.x * other.x + this.y * other.y
}