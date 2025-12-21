package com.kgame.plugins.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.ecs.Entity

/**
 * Mark camera follow target.
 */
data class CameraTarget(val entity: Entity) : Component<CameraTarget> {
    override fun type() = CameraTarget

    companion object : ComponentType<CameraTarget>()
}

/**
 * The dead zone of the camera.
 */
data class CameraDeadZone(
    val size: Size,
    val offset: Offset = Offset.Zero
): Component<CameraDeadZone> {
    override fun type() = CameraDeadZone

    companion object : ComponentType<CameraDeadZone>()
}


/**
 * Camera Shake Component: Attached to the currently active camera entity
 * that is shaking.
 */
data class CameraShake(
    var trauma: Float = 0f,
    var traumaDecay: Float = 3.2f,
    var maxShakeOffset: Float = 12f,
    var maxShakeAngle: Float = 1.8f,
    var shakeOffset: Offset = Offset.Zero,
    var shakeRotation: Float = 0f,
): Component<CameraShake> {
    override fun type() = CameraShake

    companion object : ComponentType<CameraShake>()
}

/**
 * Camera Transition Component: Attached to the currently active camera entity
 * that is initiating a smooth switch.
 */
data class CameraTransition(
    val targetCamera: String,
    val targetPosition: Offset,
    val duration: Float,

    val finishTracking: Boolean = false
) : Component<CameraTransition> {
    override fun type() = CameraTransition
    companion object: ComponentType<CameraTransition>()

    internal var elapsedTime: Float = 0f
    internal var startPosition: Offset? = null
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
 * @param name The name of the camera. If it is multi-camera, the name must be unique.
 * @param isActive Whether the camera is active.
 * @param isMain Whether the camera is the main camera.
 * @param isTracking Whether the camera is tracking the [CameraTarget].
 * @param viewport The viewport of the camera.
 * @param bounds The bounds of the camera.
 */
class Camera(
    val name: String = "",

    var isMain: Boolean = false,
    var isActive: Boolean = true,
    var isTracking: Boolean = true,

    var bounds: Rect = Rect.Zero,
    var viewport: Viewport = Viewport(0f, 0f, 1f, 1f),
) : Component<Camera> {

    override fun type() = Camera

    companion object : ComponentType<Camera>()

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

/**
 * Clamps the camera's center position to ensure its viewport remains within the world boundaries.
 * This is the definitive, correct implementation based on calculating the intersection
 * of the camera's potential movable area and the world bounds.
 *
 * It correctly handles all scenarios, including multi-viewport (split-screen) setups,
 * and worlds that are larger or smaller than the viewport.
 *
 * @param position The raw world position (Offset) of the camera center to be clamped.
 * @param viewportSize The total virtual size of the canvas/screen.
 * @return The clamped world position (Offset).
 */
fun Camera.clampToBounds(position: Offset, viewportSize: Size): Offset {
    val cameraBounds = this.bounds
    if (cameraBounds.isEmpty) {
        return position
    }

    val viewW = viewportSize.width  * viewport.width
    val viewH = viewportSize.height * viewport.height

    val halfW = viewW / 2f
    val halfH = viewH / 2f

    val minX: Float
    val maxX: Float

    if (viewW > cameraBounds.width) {
        minX = cameraBounds.left
        maxX = cameraBounds.right
    } else {
        minX = cameraBounds.left + halfW
        maxX = cameraBounds.right - halfW
    }

    val minY: Float
    val maxY: Float

    if (viewH > cameraBounds.height) {
        minY = cameraBounds.top
        maxY = cameraBounds.bottom
    } else {
        minY = cameraBounds.top + halfH
        maxY = cameraBounds.bottom - halfH
    }

    return Offset(
        x = position.x.coerceIn(minX, maxX),
        y = position.y.coerceIn(minY, maxY)
    )
}


/**
 * Calculates a new position for a game object, clamped within this Camera's viewport bounds.
 *
 * This function takes the desired world position and size of a game object, and returns a new,
 * clamped position ensuring the object remains fully visible within the camera's current view.
 * The camera's current center position must be provided externally, as the Camera class itself
 * does not store its position state. If the object is larger than the viewport, it will be
 * centered as a reasonable fallback.
 *
 * @param position The desired top-left world-space position of the object.
 * @param size The size of the object.
 * @param cameraCenter The current center position of the camera in world-space.
 * @param viewportSize The actual render size of the physical viewport (e.g., the Composable's size) in pixels.
 * @return The new, clamped world-space position where the object should be placed.
 */
fun Camera.clampToViewport(
    position: Offset,
    size: Size,
    cameraCenter: Offset,
    viewportSize: Size
): Offset {
    // 1. Calculate the precise world-space boundaries of the current viewport.
    val viewW = viewportSize.width * this.viewport.width
    val viewH = viewportSize.height * this.viewport.height
    val viewportLeft = cameraCenter.x - viewW / 2f
    val viewportTop = cameraCenter.y - viewH / 2f
    val viewportRight = cameraCenter.x + viewW / 2f
    val viewportBottom = cameraCenter.y + viewH / 2f

    // 2. Determine the valid range for the object's *center* point.
    val objectHalfW = size.width / 2f
    val objectHalfH = size.height / 2f

    // The minimum center X is the viewport's left edge plus half the object's width.
    // This ensures the object's left edge aligns with the viewport's left edge.
    val minX = viewportLeft + objectHalfW
    // The maximum center X is the viewport's right edge minus half the object's width.
    // This ensures the object's right edge aligns with the viewport's right edge.
    val maxX = viewportRight - objectHalfW

    // The same logic applies to the y-axis.
    val minY = viewportTop + objectHalfH
    val maxY = viewportBottom - objectHalfH

    // 3. Handle the edge case where the object is larger than the viewport.
    if (minX > maxX || minY > maxY) {
        // In this scenario, the object is bigger than the view. The only sensible
        // position is to align the object's center with the viewport's center.
        return cameraCenter
    }

    // 4. Clamp the desired *center* position within the calculated valid range.
    return Offset(
        x = position.x.coerceIn(minX, maxX),
        y = position.y.coerceIn(minY, maxY)
    )
}


