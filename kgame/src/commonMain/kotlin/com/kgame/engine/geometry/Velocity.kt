package com.kgame.engine.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlin.math.sqrt

/**
 * Extension properties and methods for Velocity to support vector mathematics.
 */
val Velocity.magnitudeSquared: Float get() = x * x + y * y
val Velocity.magnitude: Float get() = sqrt(magnitudeSquared)

/**
 * Returns a unit vector (magnitude of 1) in the same direction.
 */
fun Velocity.normalized(): Velocity {
    val mag = magnitude
    return if (mag > 1e-6f) { // Use a small epsilon to avoid division by zero
        Velocity(x / mag, y / mag)
    } else {
        Velocity.Zero
    }
}

/**
 * Converts this [Velocity] to an [Offset].
 */
fun Velocity.toOffset(): Offset = Offset(x, y)