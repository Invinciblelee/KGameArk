package com.kgame.plugins.components

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType

/**
 * Movement component defines the autonomous movement capability of an entity.
 * It separates the base cruise speed from the current directional state
 * to ensure input logic doesn't erase the entity's movement potential.
 */
class Movement(
    /** The base speed magnitude when moving on the X axis. */
    var cruiseX: Float = 0f,
    /** The base speed magnitude when moving on the Y axis. */
    var cruiseY: Float = 0f,
    /** Current normalized direction on X axis (-1f, 0f, 1f). */
    var dirX: Float = 1f,
    /** Current normalized direction on Y axis (-1f, 0f, 1f). */
    var dirY: Float = 1f
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
 * Brings the entity to a standstill by clearing its directional state.
 * The potential cruise speed remains untouched.
 */
fun Movement.standstill() {
    this.dirX = 0f
    this.dirY = 0f
}

/**
 * Instantly stops all movement and clears the speed potential.
 * Use this for permanent immobilization.
 */
fun Movement.immobilize() {
    this.dirX = 0f
    this.dirY = 0f
    this.cruiseX = 0f
    this.cruiseY = 0f
}

/**
 * Restores the movement potential of the entity by re-assigning cruise speeds.
 * This is the counterpart to [immobilize].
 *
 * @param cruiseX The base speed magnitude to restore on the X axis.
 * @param cruiseY The base speed magnitude to restore on the Y axis.
 */
fun Movement.mobilize(cruiseX: Float, cruiseY: Float) {
    this.cruiseX = cruiseX
    this.cruiseY = cruiseY
    // Note: We usually keep dirX/Y at 0f until new input arrives
    // to prevent the entity from "teleporting" or sudden jerks.
}

/**
 * Steps the movement forward by a given direction and time, automatically updating the transform.
 */
fun Movement.step(transform: Transform, dirX: Float, dirY: Float, deltaTime: Float) {
    this.applyDirection(dirX, dirY)
    transform.applyKinematicMovement(this, deltaTime)
}