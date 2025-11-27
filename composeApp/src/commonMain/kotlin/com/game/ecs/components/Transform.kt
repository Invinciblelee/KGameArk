package com.game.ecs.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import com.game.ecs.Component
import com.game.ecs.ComponentType
import kotlin.math.sqrt

data class Transform(
    var position: Offset = Offset.Zero,
    var rotation: Float = 0f,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f
) : Component<Transform> {
    override fun type() = Transform
    companion object : ComponentType<Transform>()
}

/**
 * Encapsulates a damped harmonic oscillator (DHO) to smoothly move a Transform toward a target.
 * The method updates the Transform's position and directly mutates the smooth-velocity state
 * stored inside the SpringEffect.
 *
 * @param deltaTime    Time step.
 * @param effect       Spring-effect configuration (stiffness k, damping c, and velocity state).
 * @param targetPosition Target position in world coordinates.
 * @param mass         Mass of the body being driven.
 */
fun Transform.applySpringFollow(
    deltaTime: Float,
    effect: SpringEffect,
    targetPosition: Offset,
    mass: Float = 1f
) {
    var currentVelocity = effect.velocity
    val currentPosition = this.position

    val displacement = currentPosition - targetPosition

    // F = -k*x - c*v
    val forceSpring = displacement * -effect.stiffness
    val forceDamping = currentVelocity * -effect.damping
    val totalForce = forceSpring + forceDamping

    val acceleration = totalForce / mass

    // Velocity (v = v + a*dt)
    currentVelocity += acceleration * deltaTime
    effect.velocity = currentVelocity

    // Position (x = x + v*dt)
    val newX = currentPosition.x + currentVelocity.x * deltaTime
    val newY = currentPosition.y + currentVelocity.y * deltaTime

    this.position = Offset(newX, newY)
}

/**
 * Encapsulates linear-interpolation (Lerp) logic to move the Transform toward a target
 * position over time, controlled by a smoothness factor.
 *
 * @param deltaTime      Time step.
 * @param targetPosition Target position in world coordinates.
 * @param lerpSpeed      Velocity multiplier that determines the Lerp factor.
 */
fun Transform.applyLerpFollow(
    deltaTime: Float,
    targetPosition: Offset,
    lerpSpeed: Float
) {
    val t = (lerpSpeed * deltaTime).coerceIn(0f, 1f)

    val newX = lerp(this.position.x, targetPosition.x, t)
    val newY = lerp(this.position.y, targetPosition.y, t)

    this.position = Offset(newX, newY)
}

/**
 * Computes and updates the Transform's position from raw input direction (delta) and speed.
 * Normalizes the input so diagonal movement is not faster.
 *
 * @param deltaTime Time step (dt).
 * @param rawDeltaX Raw X-axis input (-1f, 0f, 1f).
 * @param rawDeltaY Raw Y-axis input (-1f, 0f, 1f).
 * @param speed     Movement speed (e.g. 20f).
 */
fun Transform.applyMovement(
    deltaTime: Float,
    rawDeltaX: Float,
    rawDeltaY: Float,
    speed: Float
) {
    var deltaX = rawDeltaX
    var deltaY = rawDeltaY

    val length = sqrt(deltaX * deltaX + deltaY * deltaY)
    if (length > 0) {
        deltaX /= length
        deltaY /= length
    }

    val movementX = deltaX * speed * deltaTime
    val movementY = deltaY * speed * deltaTime

    this.position = Offset(
        x = this.position.x + movementX,
        y = this.position.y + movementY
    )
}

/**
 * Computes and updates the Transform's position from a CameraTransition.
 * @param cameraTransition The CameraTransition component to use for the transition.
 * @param deltaTime The time elapsed since the last frame.
 * @return The raw progress of the transition (0f to 1f).
 */
fun Transform.applyCameraTransform(
    cameraTransition: CameraTransition,
    deltaTime: Float
): Float {
    val startPos = cameraTransition.startPosition ?: return 0f

    // 2. Update elapsed time and raw progress
    cameraTransition.elapsed += deltaTime
    val rawProgress = (cameraTransition.elapsed / cameraTransition.duration).coerceIn(0f, 1f)

    // 3. Calculate smooth curve using Smoothstep (Ease-In-Out)
    val easedProgress = rawProgress * rawProgress * (3f - 2f * rawProgress)

    // 4. Interpolate position: Start -> End
    val newX = startPos.x + (cameraTransition.targetPosition.x - startPos.x) * easedProgress
    val newY = startPos.y + (cameraTransition.targetPosition.y - startPos.y) * easedProgress

    this.position = Offset(newX, newY)
    return rawProgress
}