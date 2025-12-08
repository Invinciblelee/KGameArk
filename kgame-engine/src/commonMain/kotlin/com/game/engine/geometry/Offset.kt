package com.game.engine.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ScaleFactor
import com.game.engine.geometry.normalized
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
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
