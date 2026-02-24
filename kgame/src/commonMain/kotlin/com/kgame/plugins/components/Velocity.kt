package com.kgame.plugins.components

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import kotlin.math.abs

/**
 * Vector component representing movement velocity in units per second.
 */
data class Velocity(
    var x: Float = 0f,
    var y: Float = 0f
) : Component<Velocity> {
    override fun type() = Velocity
    companion object Companion : ComponentType<Velocity>()
}

/**
 * Toggles the direction of X velocity.
 */
fun Velocity.reverseX() {
    this.x = -this.x
}

/**
 * Toggles the direction of Y velocity.
 */
fun Velocity.reverseY() {
    this.y = -this.y
}

/**
 * Updates velocity direction based on input (-1, 0, 1).
 * Supports updating a single axis by leaving the other null.
 */
fun Velocity.applyDirection(
    dirX: Float? = null,
    dirY: Float? = null
) {
    // Only update if a value is provided, preserving original speed magnitude
    dirX?.let { this.x = it * abs(this.x) }
    dirY?.let { this.y = it * abs(this.y) }
}