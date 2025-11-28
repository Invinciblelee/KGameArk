package com.game.plugins.components

import androidx.compose.ui.geometry.Offset
import com.game.engine.math.normalize
import com.game.ecs.Component
import com.game.ecs.ComponentType
import kotlin.math.exp

/**
 * A component of Rigidbody, used to simulate physics.
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
 */
data class Arriver(
    var speed: Float = 200f,
    var stopDistance: Float = 1f,
    var arriveEnabled: Boolean = true
) : Component<Arriver> {
    override fun type() = Arriver
    companion object Companion : ComponentType<Arriver>()
}

/**
 * A component of Spring, used to control the spring effect of an entity.
 * @param stiffness Stiffness of the spring.
 * @param damping Damping of the spring.
 */
data class Spring(
    var stiffness: Float = 300f,
    var damping: Float = 15f,
) : Component<Spring> {
    override fun type() = Spring
    companion object Companion : ComponentType<Spring>()
}

/**
 * Add a force to the rigidBody.
 * F = ma => a = F/m, where F is the force and a is the acceleration.
 * @param force The force to add.
 */
fun RigidBody.addForce(force: Offset) {
    this.acceleration += force / this.mass
}

/**
 * Add an impulse to the rigidBody.
 * I = F * dt => v += I/m * dt, where I is the impulse and v is the velocity.
 * @param impulse The impulse to add.
 */
fun RigidBody.addImpulse(impulse: Offset) {
    this.velocity += impulse / this.mass
}

/**
 * Add a torque to the rigidBody.
 * T = Iα => α = T/I, where T is the torque and α is the angular acceleration.
 * @param torque The torque to add.
 */
fun RigidBody.addTorque(torque: Float) {
    this.angularAcceleration += torque / this.inertia
}

/**
 * Add force and torque to the rigidBody.
 * This method is equivalent to calling addForce and addTorque in sequence.
 * @param force (F)
 * @param point point of force application (world coordinates)
 * @param center center of mass of the rigid body (Transform.position)
 */
fun RigidBody.addForceAtPosition(force: Offset, point: Offset, center: Offset) {
    this.addForce(force)
    val r = point - center
    val torque = r.x * force.y - r.y * force.x
    this.addTorque(torque)
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
 * @param magnitude    strength of the impulse to apply (scalar).
 */
fun RigidBody.applyImpulseFromSegment(
    segmentStart: Offset,
    segmentEnd: Offset,
    center: Offset,
    magnitude: Float
) {
    val segmentVector = segmentEnd - segmentStart
    val centerToStart = center - segmentStart
    val segmentLengthSq = segmentVector.getDistanceSquared()

    if (segmentLengthSq == 0f) return

    var t = (centerToStart.x * segmentVector.x + centerToStart.y * segmentVector.y) / segmentLengthSq
    t = t.coerceIn(0f, 1f)

    val closestPoint = segmentStart + segmentVector * t
    val collisionNormal = (center - closestPoint).normalize()
    val impulse = collisionNormal * magnitude

    this.addImpulse(impulse)
}

/**
 * Computes and applies a steering force to the Rigidbody.
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

    if (arriver.arriveEnabled) {
        val slowDownRadius = 100f
        if (dist < slowDownRadius) {
            desiredSpeed = arriver.speed * (dist / slowDownRadius)
        }
    }

    val desiredVel = diff.normalize() * desiredSpeed
    val steerForce = (desiredVel - this.velocity) * 4f

    this.addForce(steerForce)
}

/**
 * Computes and applies a spring force (elastic attraction) to the Rigidbody.
 * @param transform      The Transform component of the current entity.
 * @param spring         The Spring configuration.
 * @param targetPosition Anchor point of the spring in world coordinates.
 */
fun RigidBody.applySpringForce(
    transform: Transform,
    spring: Spring,
    targetPosition: Offset
) {
    val displacement = transform.position - targetPosition

    // F_spring = -k * x
    val forceSpring = displacement * -spring.stiffness

    // F_damping = -c * v
    val forceDamping = this.velocity * -spring.damping

    this.addForce(forceSpring + forceDamping)
}

/**
 * Physics integration: updates the Transform from the Rigidbody’s velocity and acceleration.
 * This method completely replaces the core loop logic of PhysicsSystem.
 * @param transform The Transform component of the current entity.
 * @param deltaTime Time step for integration.
 */
fun RigidBody.integrate(transform: Transform, deltaTime: Float) {
    // v = v * e^(-drag * dt)
    val dragFactor = exp(-this.drag * deltaTime)
    this.velocity *= dragFactor

    // 2. v += a * dt
    this.velocity += this.acceleration * deltaTime

    // 3. limit max speed
    val maxSpeedSq = this.maxSpeed * this.maxSpeed
    if (this.velocity.getDistanceSquared() > maxSpeedSq) {
        this.velocity = this.velocity.normalize() * this.maxSpeed
    }

    // 4. p += v * dt
    transform.position += this.velocity * deltaTime

    // 5. Angular Drag
    val angularDragFactor = exp(-this.angularDrag * deltaTime)
    this.angularVelocity *= angularDragFactor

    // 6. rotation += angularVelocity * dt
    transform.rotation += this.angularVelocity * deltaTime

    // 7. reset
    this.acceleration = Offset.Zero
}