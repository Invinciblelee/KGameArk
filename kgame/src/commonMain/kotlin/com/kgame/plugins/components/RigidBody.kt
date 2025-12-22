package com.kgame.plugins.components

import androidx.compose.ui.geometry.Offset
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.engine.geometry.normalized
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * A component of RigidBody, used to simulate physics.
 * @param velocity Current velocity.
 * @param acceleration Current acceleration.
 * @param mass Mass of the entity.
 * @param drag Linear drag coefficient.
 * @param maxSpeed Maximum speed limit.
 * @param angularVelocity Current angular velocity(rad/s).
 * @param angularAcceleration Current angular acceleration(rad/s²).
 * @param angularDrag Angular drag coefficient.
 * @param inertia Moment of inertia(Moment of Inertia, I).
 */
data class RigidBody(
    var velocity: Offset = Offset.Zero,
    var acceleration: Offset = Offset.Zero,
    var mass: Float = 1f,
    var drag: Float = 2.0f,
    var maxSpeed: Float = 1000f,

    var angularVelocity: Float = 0f,
    var angularAcceleration: Float = 0f,
    var angularDrag: Float = 5.0f,
    var inertia: Float = 1f
) : Component<RigidBody> {
    override fun type() = RigidBody

    companion object Companion : ComponentType<RigidBody>()
}

/**
 * A component of Arriver, used to control the arrive behavior of an entity.
 * @param speed Arriver speed.
 * @param stopDistance Distance to stop.
 * @param arriveEnabled Whether to enable arrive behavior.
 * @param slowDownRadius The radius within which the entity will start to slow down.
 */
data class Arriver(
    var speed: Float = 200f,
    var stopDistance: Float = 1f,
    var arriveEnabled: Boolean = true,
    val slowDownRadius: Float = 100f
) : Component<Arriver> {
    override fun type() = Arriver

    companion object Companion : ComponentType<Arriver>()
}

/**
 * A component that enables a wandering behavior for an entity.
 * It works by projecting a "wander circle" in front of the entity and
 * steering towards a random point on that circle.
 *
 * @param distance The distance of the wander circle from the entity.
 * @param radius The radius of the wander circle.
 * @param jitter The maximum amount of random change applied to the wander angle each frame.
 * @param maxForce The maximum steering force to apply for wandering.
 */
data class Wander(
    val distance: Float = 150f,
    val radius: Float = 100f,
    val jitter: Float = 80f,
    val maxForce: Float = 150f,
    // Internal state for the wander calculation
    internal var target: Offset = Offset.Zero,
    internal var angle: Float = 0f
) : Component<Wander> {
    init {
        // Initialize with a random starting angle
        angle = Random.nextFloat() * 360.0f
    }
    override fun type() = Wander
    companion object : ComponentType<Wander>()
}

/**
 * Applies a force to the RigidBody.
 * F = ma => a = F/m, where F is the force and a is the acceleration.
 * @param force The force to add.
 */
fun RigidBody.applyForce(force: Offset) {
    this.acceleration += force / this.mass
}

/**
 * Applies an impulse to the RigidBody.
 * I = F * dt => v += I/m * dt, where I is the impulse and v is the velocity.
 * @param impulse The impulse to add.
 */
fun RigidBody.applyImpulse(impulse: Offset) {
    if (this.mass <= 0f) {
        return
    }
    this.velocity += impulse / this.mass
}

/**
 * Applies a torque to the RigidBody.
 * T = Iα => α = T/I, where T is the torque and α is the angular acceleration.
 * @param torque The torque to add.
 */
fun RigidBody.applyTorque(torque: Float) {
    this.angularAcceleration += torque / this.inertia
}

/**
 * Applies torque to align the rigid body's "forward" direction with a target direction.
 * This is useful for self-correcting projectiles, AI agents, etc.
 * @param currentRotation The current rotation of the entity (in degrees).
 * @param targetDirection The desired direction vector the entity should face.
 * @param maxTorque The maximum torque to apply to prevent overly rapid rotation.
 */
fun RigidBody.applyTorqueToAlign(
    currentRotation: Float,
    targetDirection: Offset,
    maxTorque: Float = 500f
) {
    if (targetDirection == Offset.Zero) return

    val targetAngle = kotlin.math.atan2(targetDirection.y, targetDirection.x) * (180f / kotlin.math.PI.toFloat())

    // Find the shortest angle to rotate
    var angleDiff = targetAngle - currentRotation
    while (angleDiff > 180f) angleDiff -= 360f
    while (angleDiff < -180f) angleDiff += 360f

    // Apply a proportional torque. The further away from the target, the stronger the torque.
    // A damping factor is also useful to prevent overshooting.
    val desiredTorque = angleDiff * 10f // Proportional control (P-controller)
    val dampingTorque = -this.angularVelocity * 5f // Damping control (D-controller)

    val totalTorque = (desiredTorque + dampingTorque).coerceIn(-maxTorque, maxTorque)

    this.applyTorque(totalTorque)
}

/**
 * Applies force and torque to the RigidBody.
 * This method is equivalent to calling addForce and addTorque in sequence.
 * @param force (F)
 * @param point point of force application (world coordinates)
 * @param center center of mass of the rigid body (Transform.position)
 */
fun RigidBody.applyForceAtPosition(force: Offset, point: Offset, center: Offset) {
    this.applyForce(force)
    val r = point - center
    val torque = r.x * force.y - r.y * force.x
    this.applyTorque(torque)
}

/**
 * Applies an explosion force to the rigid body.
 * The force is directed away from the explosion's center and its magnitude
 * decreases with distance.
 *
 * @param explosionCenter The world position of the explosion's center.
 * @param explosionForce The base force magnitude at the center of the explosion.
 * @param explosionRadius The maximum radius of the explosion's effect.
 * @param position The world position of this rigid body's center.
 */
fun RigidBody.applyExplosionForce(
    explosionCenter: Offset,
    explosionForce: Float,
    explosionRadius: Float,
    position: Offset
) {
    val direction = position - explosionCenter
    val distance = direction.getDistance()

    if (distance >= explosionRadius || distance == 0f) {
        return
    }

    // Force decreases as the square of the distance (can be adjusted)
    val wearoff = 1f - (distance / explosionRadius)
    val forceMagnitude = explosionForce * wearoff * wearoff

    val force = direction.normalized() * forceMagnitude

    this.applyForce(force)
}

/**
 * Applies a fluid drag force (like air or water resistance) to the rigid body.
 * This force is proportional to the square of the velocity and opposes the motion.
 * F_drag = -0.5 * C * A * rho * v^2
 * We can simplify this to F_drag = -k * |v| * v
 * @param dragCoefficient A constant that combines factors like fluid density, cross-sectional area, etc.
 */
fun RigidBody.applyDragForce(dragCoefficient: Float = 0.1f) {
    if (this.velocity == Offset.Zero) return
    val speed = this.velocity.getDistance()
    val dragForceMagnitude = dragCoefficient * speed * speed
    val dragForce = this.velocity.normalized() * -dragForceMagnitude
    this.applyForce(dragForce)
}


/**
 * Computes and applies a steering force to the RigidBody.
 * @param transform      The Transform component of the current entity.
 * @param arriver        The Arriver configuration.
 * @param targetPosition Target position in world coordinates.
 */
fun RigidBody.applyArriverForce(
    transform: Transform,
    arriver: Arriver,
    targetPosition: Offset
) {
    val diff = targetPosition - transform.position
    val dist = diff.getDistance()

    if (dist < arriver.stopDistance) {
        this.velocity = Offset.Zero
        this.acceleration = Offset.Zero
        return
    }

    var desiredSpeed = arriver.speed

    if (arriver.arriveEnabled && arriver.slowDownRadius > 0) {
        // Use the configurable slowDownRadius from the Arriver component.
        if (dist < arriver.slowDownRadius) {
            // Map the distance to a speed multiplier (from max speed down to 0)
            desiredSpeed = arriver.speed * (dist / arriver.slowDownRadius)
        }
    }

    val desiredVel = diff.normalized() * desiredSpeed
    val steerForce = (desiredVel - this.velocity) * 4f

    this.applyForce(steerForce)
}

/**
 * Computes and applies a spring force (elastic attraction) to the RigidBody.
 * @param transform      The Transform component of the current entity.
 * @param elasticity     The Elasticity configuration.
 * @param targetPosition Anchor point of the spring in world coordinates.
 */
fun RigidBody.applyElasticityForce(
    transform: Transform,
    elasticity: Elasticity,
    targetPosition: Offset
) {
    val displacement = transform.position - targetPosition

    // F_spring = -k * x
    val forceSpring = displacement * -elasticity.stiffness

    // F_damping = -c * v
    val forceDamping = this.velocity * -elasticity.damping

    this.applyForce(forceSpring + forceDamping)
}

/**
 * Calculates and applies the wandering force to a rigid body.
 * This behavior creates a seemingly random, organic movement.
 *
 * This function's logic only depends on the body's current velocity and its wander parameters,
 * making it a self-contained steering behavior. It should be called from within the SteeringSystem.
 */
fun RigidBody.applyWanderForce(wander: Wander) {
    // 1. Project the wander circle's center in front of the entity based on its velocity.
    // If velocity is zero, we create a default forward direction to avoid getting stuck.
    val circleCenterDirection = if (this.velocity != Offset.Zero) {
        this.velocity.normalized()
    } else {
        // Start by wandering in a random direction if standing still
        Offset(cos(wander.angle * (PI / 180f).toFloat()), sin(wander.angle * (PI / 180f).toFloat()))
    }
    val circleCenter = circleCenterDirection * wander.distance

    // 2. Calculate the random displacement on the circle
    val displacement = Offset(0f, -1f) * wander.radius

    // Apply jitter to the angle for the next frame
    wander.angle += (Random.nextFloat() * 2f - 1f) * wander.jitter

    // Rotate the displacement vector by the new jittered angle
    val angleRad = wander.angle * (PI / 180f).toFloat()
    val cos = cos(angleRad)
    val sin = sin(angleRad)
    val rotatedDisplacement = Offset(
        x = displacement.x * cos - displacement.y * sin,
        y = displacement.x * sin + displacement.y * cos
    )

    // 3. The final wander force is the sum of the circle center and the displacement
    val wanderForce = (circleCenter + rotatedDisplacement).normalized() * wander.maxForce

    // Apply the force
    this.applyForce(wanderForce)
}

/**
 * Applies linear and angular drag (damping) to the rigid body's velocities over a given time step.
 * This is useful for manually controlling damping outside of the main integration step.
 * @param deltaTime The time step over which to apply the damping.
 */
fun RigidBody.applyDamping(deltaTime: Float) {
    this.velocity *= exp(-this.drag * deltaTime)
    this.angularVelocity *= exp(-this.angularDrag * deltaTime)
}

/**
 * Adds a gravitational force to the rigid body.
 *
 * This is a convenience method for applying gravity. It assumes the provided 'gravity'
 * is an acceleration vector (like g = 9.8 m/s²). It calculates the gravitational
 * force (F = m * g) and adds it to the body's total forces for this frame.
 * This method should typically be called once per frame from within a `PhysicsSystem`.
 *
 * @param gravity The gravitational acceleration vector (e.g., Offset(0f, 980f)).
 */
fun RigidBody.applyGravity(gravity: Offset) {
    if (this.mass <= 0f) return
    // F = m * g. We add this force to the body.
    val gravityForce = gravity * this.mass
    this.applyForce(gravityForce)
}

/**
 * Applies a normal impulse to the rigid body based on collision info with a line segment.
 *
 * This method handles the collision response between a circle (rigid body) and a segment
 * (e.g., sword wire). It computes the exact normal that pushes the body away from the
 * segment and applies the corresponding impulse.
 *
 * @param segmentStart start point P1 of the segment (world coordinates).
 * @param segmentEnd   end point P2 of the segment (world coordinates).
 * @param center       center C of the colliding entity (i.e., circle’s center).
 * @param impulseMag   strength of the impulse to apply (scalar).
 */
fun RigidBody.applyImpulseFromSegment(
    segmentStart: Offset,
    segmentEnd: Offset,
    center: Offset,
    impulseMag: Float
) {
    if (this.mass <= 0f) return

    val segmentVector = segmentEnd - segmentStart
    val centerToStart = center - segmentStart
    val segmentLengthSq = segmentVector.getDistanceSquared()

    if (segmentLengthSq == 0f) return

    var t = (centerToStart.x * segmentVector.x + centerToStart.y * segmentVector.y) / segmentLengthSq
    t = t.coerceIn(0f, 1f)

    val closestPoint = segmentStart + segmentVector * t

    val collisionNormal = (center - closestPoint).normalized()
    val impulse = collisionNormal * impulseMag

    this.applyImpulse(impulse)
}

/**
 * Calculates the total kinetic energy of the rigid body.
 * Kinetic Energy = 0.5 * m * v^2 (linear) + 0.5 * I * w^2 (angular).
 * @return The total kinetic energy (linear + angular).
 */
fun RigidBody.getKineticEnergy(): Float {
    val linearKE = 0.5f * this.mass * this.velocity.getDistanceSquared()
    val angularKE = 0.5f * this.inertia * this.angularVelocity * this.angularVelocity
    return linearKE + angularKE
}

/**
 * Checks if the rigid body is effectively "sleeping" or at rest.
 *
 * @param linearThreshold The velocity magnitude below which the body is considered linearly at rest.
 * @param angularThreshold The angular velocity below which the body is considered angularly at rest.
 * @return `true` if both linear and angular velocities are below their respective thresholds.
 */
fun RigidBody.isSleeping(linearThreshold: Float = 0.1f, angularThreshold: Float = 0.1f): Boolean {
    return this.velocity.getDistanceSquared() < linearThreshold * linearThreshold &&
            kotlin.math.abs(this.angularVelocity) < angularThreshold
}

/**
 * Sets the velocity, acceleration, angular velocity, and angular acceleration of a rigid body to zero.
 */
fun RigidBody.still() {
    this.velocity = Offset.Zero
    this.acceleration = Offset.Zero
    this.angularVelocity = 0f
    this.angularAcceleration = 0f
}

/**
 * Apply collision response between two rigid bodies.
 * @param r1 The first rigid body.
 * @param t1 The transform of the first rigid body.
 * @param r2 The second rigid body.
 * @param t2 The transform of the second rigid body.
 * @param separation The separation vector between the two rigid bodies.
 * @param impulseMag The magnitude of the impulse to apply.
 */
fun RigidBody.Companion.applyCollision(
    r1: RigidBody, t1: Transform,
    r2: RigidBody, t2: Transform,
    separation: Offset,
    impulseMag: Float = 30f
) {
    val invM1 = if (r1.mass <= 0f) 0f else 1f / r1.mass
    val invM2 = if (r2.mass <= 0f) 0f else 1f / r2.mass
    val totalInv = invM1 + invM2
    if (totalInv <= 0f) return

    val corr = separation / totalInv
    if (r1.mass > 0f) t1.position += corr * invM1
    if (r2.mass > 0f) t2.position -= corr * invM2

    val normal = separation.normalized()
    val impulse = normal * impulseMag
    r1.applyImpulse(impulse * invM1)
    r2.applyImpulse(-impulse * invM2)
}

/**
 * Physics integration: updates the Transform from the RigidBody’s velocity and acceleration.
 * This method completely replaces the core loop logic of PhysicsSystem.
 * @param transform The Transform component of the current entity.
 * @param deltaTime Time step for integration. */
fun RigidBody.integrate(transform: Transform, deltaTime: Float) {
    // --- Linear Motion ---
    // 1. Apply linear drag to velocity
    // v = v * e^(-drag * dt)
    val dragFactor = exp(-this.drag * deltaTime)
    this.velocity *= dragFactor

    // 2. Apply acceleration to velocity
    // v += a * dt
    this.velocity += this.acceleration * deltaTime

    // 3. Limit max speed
    val maxSpeedSq = this.maxSpeed * this.maxSpeed
    if (this.velocity.getDistanceSquared() > maxSpeedSq) {
        this.velocity = this.velocity.normalized() * this.maxSpeed
    }

    // 4. Update position based on the new velocity
    // p += v * dt
    transform.position += this.velocity * deltaTime

    // --- Angular Motion ---
    // 5. Apply angular drag to angular velocity
    val angularDragFactor = exp(-this.angularDrag * deltaTime)
    this.angularVelocity *= angularDragFactor

    // 6. Apply angular acceleration to angular velocity
    this.angularVelocity += this.angularAcceleration * deltaTime

    // 7. Update rotation based on the new angular velocity
    transform.rotation += this.angularVelocity * deltaTime

    // 8. Reset both linear and angular accelerations for the next frame
    this.acceleration = Offset.Zero
    this.angularAcceleration = 0f
}
