package com.kgame.engine.graphics.drawscope

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.kgame.engine.geometry.ResolutionScaleType
import com.kgame.engine.geometry.ResolutionManager
import com.kgame.engine.geometry.toOffset
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.Transform

/**
 * An extension for `DrawScope` that applies a viewport-adaptive translation and scale.
 * Inside the [block], the coordinate system is transformed into the kgame's virtual coordinate system.
 *
 * @param transform The ViewportTransform instance containing the scale factor and offset.
 * @param block The drawing logic to be executed within the virtual coordinate system.
 */
inline fun DrawScope.withViewportTransform(
    transform: ResolutionManager,
    block: DrawScope.() -> Unit
) {
    withTransform({
        if (transform.scaleType == ResolutionScaleType.Fill) {
            translate(transform.offsetX, transform.offsetY)
        }
        scale(transform.scaleFactor, transform.scaleFactor, pivot = Offset.Zero)
    }) {
        block()
    }
}

/**
 * Centers the coordinate system at the screen midpoint.
 * This provides a camera-like origin for scenes that do not use a real Camera entity.
 */
inline fun DrawScope.withCenteredTransform(
    block: DrawScope.() -> Unit
) {
    withTransform({
        translate(size.width / 2f, size.height / 2f)
    }) {
        block()
    }
}

/**
 * An extension for `DrawScope` that applies the camera's translation, scale, and rotation.
 * Inside the [block], the coordinate system is transformed into the camera's view space.
 * This version correctly handles camera bounds, shake, and viewport clipping.
 *
 * @param camera The camera component, providing viewport settings, zoom, and its own rotation.
 * @param transform The Transform component of the camera entity, providing position and entity rotation.
 * @param shake Optional camera shake effect.
 * @param block The drawing logic to be executed within the camera's coordinate system.
 */
inline fun DrawScope.withCameraTransform(
    camera: Camera,
    transform: Transform,
    shake: CameraShake?,
    block: DrawScope.() -> Unit
) {
    val viewport = camera.viewport

    val viewportL = size.width * viewport.left
    val viewportT = size.height * viewport.top
    val viewportW = size.width * viewport.width
    val viewportH = size.height * viewport.height

    val halfViewportW = viewportW / 2f
    val halfViewportH = viewportH / 2f

    val cameraPos = transform.position
    val cameraScale = transform.scale
    val cameraRotation = transform.rotation

    val shakeOffsetX = shake?.shakeOffset?.x ?: 0f
    val shakeOffsetY = shake?.shakeOffset?.y ?: 0f
    val shakeRotation = shake?.shakeRotation ?: 0f
    val finalRotation = cameraRotation + shakeRotation

    var finalCameraX = cameraPos.x
    var finalCameraY = cameraPos.y

    finalCameraX += shakeOffsetX
    finalCameraY += shakeOffsetY

    withTransform({
        clipRect(
            left = viewportL,
            top = viewportT,
            right = viewportL + viewportW,
            bottom = viewportT + viewportH
        )
        translate(left = viewportL + halfViewportW, top = viewportT + halfViewportH)
        scale(scaleX = cameraScale.scaleX, scaleY = cameraScale.scaleY, Offset.Zero)
        rotate(degrees = -finalRotation, Offset.Zero)
        translate(left = -finalCameraX, top = -finalCameraY)
    }) {
        block()
    }
}


/**
 * Sets up a local transformation for an entity in the world coordinate system and provides
 * a safe, size-overridden drawing context. This function transforms the DrawScope into the
 * entity's own local coordinate system, allowing the drawing code inside the block to
 * be written simply, relative to an origin of (0, 0) and bounded by its size.
 *
 * @param transform The Transform component containing the entity's world position, rotation, and scale.
 * @param block The entity's local drawing logic (e.g., drawCircle(Color.Red)).
 */
inline fun DrawScope.withLocalTransform(
    transform: Transform,
    size: Size,
    block: DrawScope.() -> Unit
) {
    val oldSize = drawContext.size
    try {
        val currentSize = size.let { if (it.isSpecified) it else oldSize }
        drawContext.size = currentSize

        val halfW = currentSize.width / 2f
        val halfH = currentSize.height / 2f

        val position = transform.position
        val rotation = transform.rotation
        val scale = transform.scale

        withTransform({
            translate(position.x - halfW, position.y - halfH)
            if (rotation != 0f) {
                rotate(rotation, currentSize.toOffset(transform.rotationPivot))
            }
            if (scale.scaleX != 1f || scale.scaleY != 1f) {
                scale(scale.scaleX, scale.scaleY, currentSize.toOffset(transform.scalePivot))
            }
        }) {
            block()
        }
    } finally {
        // Restore the original size of the draw context.
        drawContext.size = oldSize
    }
}

private val DebugStroke = Stroke(width = 1f)

/**
 * Draws a debug visualization for a Transform in the world coordinate system.
 * This function accurately mirrors the transformation logic of `withLocalTransform`
 * to ensure the debug visualization perfectly matches the rendered entity.
 *
 * @param transform The Transform component to visualize.
 * @param color The color to use for the debug graphics.
 */
fun DrawScope.drawDebugBounds(
    transform: Transform,
    size: Size,
    color: Color = Color.Green
) {
    if (size.isUnspecified) return

    // Calculate the top-left corner of the AABB based on the entity's center position and size.
    // This is the only CPU calculation needed.
    val topLeftX = transform.position.x - size.width / 2f
    val topLeftY = transform.position.y - size.height / 2f

    // Draw a simple, non-rotated rectangle.
    // This is extremely fast and offloads the drawing to the GPU.
    drawRect(
        color = color,
        topLeft = Offset(topLeftX, topLeftY),
        size = size,
        style = DebugStroke
    )

    // Optional: Draw a small circle or cross at the entity's exact position (its center).
    // This helps to distinguish the position from the bounding box.
    drawCircle(
        color = Color.Yellow,
        radius = 4f,
        center = transform.position
    )
}
