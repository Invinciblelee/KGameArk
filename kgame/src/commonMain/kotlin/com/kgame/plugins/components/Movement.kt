package com.kgame.plugins.components

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType

/**
 * Motion component defines the autonomous movement capability of an entity.
 * It separates the base cruise speed from the current directional state
 * to ensure input logic doesn't erase the entity's movement potential.
 */
class Movement(
    /** The base speed magnitude when moving on the X axis. */
    var cruiseX: Float = 0f,
    /** The base speed magnitude when moving on the Y axis. */
    var cruiseY: Float = 0f,
    /** Current normalized direction on X axis (-1f, 0f, 1f). */
    var dirX: Float = 0f,
    /** Current normalized direction on Y axis (-1f, 0f, 1f). */
    var dirY: Float = 0f
) : Component<Movement> {

    /** Computed instantaneous velocity on X axis. */
    val velocityX: Float get() = dirX * cruiseX
    /** Computed instantaneous velocity on Y axis. */
    val velocityY: Float get() = dirY * cruiseY

    override fun type() = Movement
    companion object Companion : ComponentType<Movement>()
}

/**
 * Reverses the current X direction.
 */
fun Movement.reverseX() {
    this.dirX = -this.dirX
}

/**
 * Reverses the current Y direction.
 */
fun Movement.reverseY() {
    this.dirY = -this.dirY
}

/**
 * Updates the movement direction based on input.
 * This implementation is safe because it modifies dirX/dirY instead of
 * multiplying against the computed velocity, preventing "zero-lock".
 */
fun Movement.applyDirection(dx: Float? = null, dy: Float? = null) {
    dx?.let { this.dirX = it }
    dy?.let { this.dirY = it }
}

/**
 * Forcefully stops the motion on both axes.
 */
fun Movement.stop() {
    this.dirX = 0f
    this.dirY = 0f
}