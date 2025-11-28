package com.game.plugins.components

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.util.lerp
import com.game.ecs.Component
import com.game.ecs.ComponentType
import com.game.engine.math.times
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class Transform(
    var position: Offset = Offset.Zero,
    var size: Size = Size.Unspecified,
    var rotation: Float = 0f,
    var rotationPivot: TransformOrigin = TransformOrigin.Center,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var scalePivot: TransformOrigin = TransformOrigin.Center
) : Component<Transform> {
    override fun type() = Transform
    companion object : ComponentType<Transform>()
}

val Transform.rotationPivotOffset: Offset
    get() = size * rotationPivot

val Transform.scalePivotOffset: Offset
    get() = size * scalePivot

/**
 * Calculates the world-axis aligned bounding box (AABB) for the entity.
 * This method correctly accounts for rotation and scaling to produce a
 * bounding box that fully encloses the transformed entity.
 * This implementation is optimized to minimize object allocations for performance.
 *
 * @param bounds The `MutableRect` instance to be updated with the calculated bounds.
 */
fun Transform.getBounds(bounds: MutableRect) {
    if (size == Size.Unspecified) {
        bounds.set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        return
    }

    val halfW = size.width / 2f
    val halfH = size.height / 2f

    // Pre-calculate rotation values
    val angleRad = rotation * (PI / 180f).toFloat()
    val sin = sin(angleRad)
    val cos = cos(angleRad)

    // Pre-calculate absolute pivot offsets in pixels
    val scalePivotX = scalePivot.pivotFractionX * size.width
    val scalePivotY = scalePivot.pivotFractionY * size.height
    val rotationPivotX = rotationPivot.pivotFractionX * size.width
    val rotationPivotY = rotationPivot.pivotFractionY * size.height

    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    // Manually process all 4 corners without creating a list or extra Offset objects
    for (i in 0..3) {
        // Define corner relative to center (0,0)
        val cornerX = if (i % 3 == 0) -halfW else halfW // corners 0,3 have -halfW; 1,2 have +halfW
        val cornerY = if (i < 2) -halfH else halfH      // corners 0,1 have -halfH; 2,3 have +halfH

        // --- Apply transformations directly on float values ---
        var px = cornerX
        var py = cornerY

        // 1. Scale around the scale pivot
        px -= scalePivotX
        py -= scalePivotY
        px *= scaleX
        py *= scaleY
        px += scalePivotX
        py += scalePivotY

        // 2. Rotate around the rotation pivot
        px -= rotationPivotX
        py -= rotationPivotY
        val rotatedX = px * cos - py * sin
        val rotatedY = px * sin + py * cos
        px = rotatedX
        py = rotatedY
        px += rotationPivotX
        py += rotationPivotY

        // 3. Translate to the final world position
        px += position.x
        py += position.y

        // 4. Update the min/max extents
        minX = min(minX, px)
        minY = min(minY, py)
        maxX = max(maxX, px)
        maxY = max(maxY, py)
    }

    // 5. Set the final bounds on the provided MutableRect
    bounds.set(minX, minY, maxX, maxY)
}

/**
 * Encapsulates a damped harmonic oscillator (DHO) to smoothly move a Transform toward a target.
 * This function calculates forces based on the Spring's properties and applies them to the
 * RigidBody, updating its velocity and, consequently, the Transform's position.
 *
 * @param targetPosition The world-space position the spring is attached to.
 * @param deltaTime      The time elapsed since the last frame.
 * @param spring         The component containing the spring's configuration (stiffness k, damping c).
 * @param rigidBody      The component holding the entity's physical state (mass, velocity).
 */
fun Transform.applySpringFollow(
    targetPosition: Offset,
    deltaTime: Float,
    spring: Spring,
    rigidBody: RigidBody // The RigidBody component is now the single source of truth for velocity and mass
) {
    // This function simulates spring physics (Hooke's Law + Damping).
    // F_total = F_spring + F_damping
    // F_spring = -k * x (force pulling the object back to the target)
    // F_damping = -c * v (force slowing the object down based on its current velocity)

    // 'x' in Hooke's law is the displacement vector from the spring's equilibrium point (the target).
    val displacement = this.position - targetPosition

    // Calculate the two main forces.
    val forceSpring = displacement * -spring.stiffness
    // **CORRECTED**: Use the velocity from the RigidBody for the damping calculation.
    val forceDamping = rigidBody.velocity * -spring.damping
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
 * @param deltaTime      Time step.
 * @param lerpSpeed      Velocity multiplier that determines the Lerp factor.
 */
fun Transform.applyLerpFollow(
    targetPosition: Offset,
    deltaTime: Float,
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
fun Transform.applyKinematicMovement(
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
    transition.elapsed += deltaTime
    val rawProgress = (transition.elapsed / transition.duration).coerceIn(0f, 1f)

    // 3. Calculate smooth curve using Smoothstep (Ease-In-Out)
    val easedProgress = rawProgress * rawProgress * (3f - 2f * rawProgress)

    // 4. Interpolate position: Start -> End
    val newX = startPos.x + (transition.targetPosition.x - startPos.x) * easedProgress
    val newY = startPos.y + (transition.targetPosition.y - startPos.y) * easedProgress

    this.position = Offset(newX, newY)
    return rawProgress
}

