package com.kgame.plugins.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.lerp
import androidx.compose.ui.util.lerp
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import kotlin.math.sqrt

data class Transform(
    var position: Offset = Offset.Zero,
    var rotation: Float = 0f,
    var rotationPivot: TransformOrigin = TransformOrigin.Center,
    var scale: ScaleFactor = ScaleFactor(1f, 1f),
    var scalePivot: TransformOrigin = TransformOrigin.Center
) : Component<Transform> {
    override fun type() = Transform
    companion object : ComponentType<Transform>()
}

/**
 * Copies all transform properties (position, size, rotation, scale, pivots)
 * into the supplied [transform] instance.
 * Useful for applying the same transform to another entity without allocating
 * a new object.
 */
fun Transform.copyTo(transform: Transform) {
    transform.position      = this.position
    transform.rotation      = this.rotation
    transform.rotationPivot = this.rotationPivot
    transform.scale         = this.scale
    transform.scalePivot    = this.scalePivot
}

/**
 *
 */
fun Transform.offset(x: Float = 0f, y: Float = 0f) {
    position += Offset(x, y)
}

/**
 *
 */
fun Transform.position(x: Float = position.x, y: Float = position.y) {
    position = Offset(x, y)
}

/**
 * Encapsulates a damped harmonic oscillator (DHO) to smoothly move a Transform toward a target.
 * This function calculates forces based on the Spring's properties and applies them to the
 * RigidBody, updating its velocity and, consequently, the Transform's position.
 *
 * @param targetPosition The world-space position the spring is attached to.
 * @param elasticity     The component containing the spring's configuration (stiffness k, damping c).
 * @param rigidBody      The component holding the entity's physical state (mass, velocity).
 * @param deltaTime      The time elapsed since the last frame.
 */
fun Transform.applyElasticityFollow(
    targetPosition: Offset,
    elasticity: Elasticity,
    rigidBody: RigidBody, // The RigidBody component is now the single source of truth for velocity and mass,
    deltaTime: Float,
) {
    // This function simulates spring physics (Hooke's Law + Damping).
    // F_total = F_spring + F_damping
    // F_spring = -k * x (force pulling the object back to the target)
    // F_damping = -c * v (force slowing the object down based on its current velocity)

    // 'x' in Hooke's law is the displacement vector from the spring's equilibrium point (the target).
    val displacement = this.position - targetPosition

    // Calculate the two main forces.
    val forceSpring = displacement * -elasticity.stiffness
    // **CORRECTED**: Use the velocity from the RigidBody for the damping calculation.
    val forceDamping = rigidBody.velocity * -elasticity.damping
    val totalForce = forceSpring + forceDamping

    // a = F / m (Newton's Second Law of Motion).
    // Acceleration is calculated using the mass from the RigidBody component.
    val acceleration = totalForce / rigidBody.mass

    // v = v_initial + a * dt
    // **CORRECTED**: Update the velocity state on the RigidBody component.
    rigidBody.velocity += acceleration * deltaTime

    // p = p_initial + v * dt
    // **CORRECTED**: Update the Transform's position using the RigidBody's newly calculated velocity.
    this.position += rigidBody.velocity * deltaTime
}

/**
 * Encapsulates linear-interpolation (Lerp) logic to move the Transform toward a target
 * position over time, controlled by a smoothness factor.
 *
 * @param targetPosition Target position in world coordinates.
 * @param smooth         The component containing the smooth's configuration.
 * @param deltaTime      Time step.
 */
fun Transform.applySmoothFollow(
    targetPosition: Offset,
    smooth: Smooth,
    deltaTime: Float,
) {
    val tRaw = (smooth.lerpSpeed * deltaTime).coerceIn(0f, 1f)
    val t    = tRaw * tRaw * (3 - 2 * tRaw)

    val newX = lerp(position.x, targetPosition.x, t)
    val newY = lerp(position.y, targetPosition.y, t)
    position = Offset(newX, newY)
}

/**
 * Computes and updates the Transform's position from raw input direction (delta) and speed.
 * Normalizes the input so diagonal movement is not faster.
 *
 * @param rawDeltaX Raw X-axis input (-1f, 0f, 1f).
 * @param rawDeltaY Raw Y-axis input (-1f, 0f, 1f).
 * @param speed     Movement speed (e.g. 20f).
 * @param deltaTime Time step (dt).
 */
fun Transform.applyKinematicMovement(
    rawDeltaX: Float,
    rawDeltaY: Float,
    speed: Float,
    deltaTime: Float,
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
 * @param transition The CameraTransition component to use for the transition.
 * @param deltaTime The time elapsed since the last frame.
 * @return The raw progress of the transition (0f to 1f).
 */
fun Transform.applyCameraTransition(
    transition: CameraTransition,
    deltaTime: Float
): Float {
    val startPos = transition.startPosition ?: return 0f

    // 2. Update elapsed time and raw progress
    transition.elapsedTime += deltaTime
    val rawProgress = (transition.elapsedTime / transition.duration).coerceIn(0f, 1f)

    // 3. Calculate smooth curve using Smoothstep (Ease-In-Out)
    val easedProgress = rawProgress * rawProgress * (3f - 2f * rawProgress)

    // 4. Interpolate position: Start -> End
    val newX = startPos.x + (transition.targetPosition.x - startPos.x) * easedProgress
    val newY = startPos.y + (transition.targetPosition.y - startPos.y) * easedProgress

    this.position = Offset(newX, newY)
    return rawProgress
}

/**
 * Applies a translation to the Transform.
 * @param fromPosition The starting position.
 * @param toPosition The ending position.
 * @param fraction The fraction of the translation between fromPosition and toPosition.
 */
fun Transform.applyTranslation(
    fromPosition: Offset,
    toPosition: Offset,
    fraction: Float
) {
    this.position = lerp(fromPosition, toPosition, fraction)
}

/**
 * Applies a rotation to the Transform.
 * @param degrees The rotation angle in degrees.
 */
fun Transform.applyRotation(
    degrees: Float,
    pivot: TransformOrigin = this.rotationPivot
) {
    this.rotation = degrees
    this.rotationPivot = pivot
}

/**
 * Applies a rotation to the Transform.
 * @param fromDegrees The starting rotation angle in degrees.
 * @param toDegrees The ending rotation angle in degrees.
 * @param pivot The pivot point for the rotation.
 * @param fraction The fraction of the rotation between fromDegrees and toDegrees.
 */
fun Transform.applyRotation(
    fromDegrees: Float,
    toDegrees: Float,
    pivot: TransformOrigin = this.rotationPivot,
    fraction: Float,
) {
    this.rotation = lerp(fromDegrees, toDegrees, fraction)
    this.rotationPivot = pivot
}

/**
 * Applies a scale to the Transform.
 * @param scaleX The scale factor along the X-axis.
 * @param scaleY The scale factor along the Y-axis.
 */
fun Transform.applyScale(
    scaleX: Float,
    scaleY: Float,
    pivot: TransformOrigin = this.scalePivot
) {
    this.scale = ScaleFactor(scaleX, scaleY)
    this.rotationPivot = pivot
}

/**
 * Applies a scale to the Transform.
 * @param scale The scale factor along the X-axis/Y-axis.
 */
fun Transform.applyScale(
    scale: ScaleFactor,
    pivot: TransformOrigin = this.scalePivot
) {
    this.scale = scale
    this.scalePivot = pivot
}

/**
 * Applies a scale to the Transform.
 * @param fromScale The starting scale factor along the X-axis/Y-axis.
 * @param toScale The ending scale factor along the X-axis/Y-axis.
 * @param pivot The pivot point for the scale.
 * @param fraction
 */
fun Transform.applyScale(
    fromScale: ScaleFactor,
    toScale: ScaleFactor,
    pivot: TransformOrigin = this.scalePivot,
    fraction: Float,
) {
    this.scale = lerp(fromScale, toScale, fraction)
    this.scalePivot = pivot
}