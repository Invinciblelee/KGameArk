package com.game.plugins.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.game.ecs.Component
import com.game.ecs.ComponentType
import com.game.ecs.Entity

/**
 * Mark camera follow target.
 */
data class CameraTarget(val name: String, val entity: Entity) : Component<CameraTarget> {
    override fun type() = CameraTarget

    companion object : ComponentType<CameraTarget>()
}

/**
 * Camera Transition Component: Attached to the currently active camera entity
 * that is initiating a smooth switch.
 */
data class CameraTransition(
    val targetCamera: String,
    val targetPosition: Offset,
    val duration: Float,

    var elapsed: Float = 0f,
    var startPosition: Offset? = null,

    val finishTracking: Boolean = false
) : Component<CameraTransition> {
    override fun type() = CameraTransition
    companion object: ComponentType<CameraTransition>()
}

/**
 * The viewport of the camera.
 */
data class Viewport(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

/**
 * A component of Camera.
 *
 * @param isActive Whether the camera is active.
 * @param isMain Whether the camera is the main camera.
 * @param isTracking Whether the camera is tracking the [CameraTarget].
 * @param zoom The zoom level of the camera.
 * @param rotation The rotation of the camera.
 * @param deadZone The dead zone of the camera.
 * @param worldBounds The bounds of the world.
 * @param trauma The trauma of the camera.
 * @param traumaDecay The decay of the trauma.
 * @param maxShakeOffset The maximum offset of the shake.
 * @param maxShakeAngle The maximum angle of the shake.
 * @param viewport The viewport of the camera.
 *
 */
data class Camera(
    var isActive: Boolean = true,
    var isMain: Boolean = false,

    var isTracking: Boolean = true,

    var zoom: Float = 1f,
    var rotation: Float = 0f,

    var trauma: Float = 0f,
    var traumaDecay: Float = 2.0f,
    var maxShakeOffset: Float = 50f,
    var maxShakeAngle: Float = 10f,

    var shakeOffset: Offset = Offset.Zero,
    var shakeRotation: Float = 0f,
    var viewport: Viewport = Viewport(0f, 0f, 1f, 1f),

    val deadZone: Size = Size(50f, 50f),
    val worldBounds: Rect = Rect(
        Float.NEGATIVE_INFINITY,
        Float.NEGATIVE_INFINITY,
        Float.POSITIVE_INFINITY,
        Float.POSITIVE_INFINITY
    ),
) : Component<Camera> {

    override fun type() = Camera

    companion object : ComponentType<Camera>()

}

/**
 * add trauma to camera
 * @param amount trauma amount
 */
fun Camera.addTrauma(amount: Float) {
    trauma = (trauma + amount).coerceIn(0f, 1f)
}

/**
 * Stops the camera from automatically following its target entity, keeping it static.
 */
fun Camera.pauseTracking() {
    this.isTracking = false
}

/**
 * Resumes the camera following its target entity, subject to its follow/spring settings.
 * Note: This should be called *after* any pan sequence is complete.
 */
fun Camera.resumeTracking() {
    this.isTracking = true
}

/**
 * Shakes the camera view by adding trauma.
 *
 * @param amount The maximum trauma value (clamped between 0 and 1).
 * @param decayRate The rate at which the trauma decays per second.
 * @param maxOffset The maximum pixel offset for shake (optional, overrides default).
 * @param maxAngle The maximum angle (in degrees) for shake (optional, overrides default).
 */
fun Camera.shake(
    amount: Float,
    decayRate: Float? = null,
    maxOffset: Float? = null,
    maxAngle: Float? = null
) {
    // 1. Add trauma amount (using existing logic)
    this.addTrauma(amount)

    // 2. Override decay rate and maximums if provided
    if (decayRate != null) {
        this.traumaDecay = decayRate.coerceAtLeast(0.01f) // Ensure decay is positive
    }
    if (maxOffset != null) {
        this.maxShakeOffset = maxOffset.coerceAtLeast(0f)
    }
    if (maxAngle != null) {
        this.maxShakeAngle = maxAngle.coerceAtLeast(0f)
    }
}

/**
 * Set the viewport of the camera.
 * @param left The left of the viewport, from 0 to 1.
 * @param top The top of the viewport, from 0 to 1.
 * @param width The width of the viewport, from 0 to 1.
 * @param height The height of the viewport, from 0 to 1.
 */
fun Camera.setViewport(
    left: Float,
    top: Float,
    width: Float,
    height: Float
) {
    viewport = Viewport(left, top, width, height)
}