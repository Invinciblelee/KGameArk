package com.game.engine.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.game.engine.geometry.ViewportScaleType
import com.game.engine.geometry.ViewportTransform
import com.game.engine.math.rotate
import com.game.engine.math.scale
import com.game.engine.math.times
import com.game.plugins.components.Camera
import com.game.plugins.components.Transform
import com.game.plugins.components.rotationPivotOffset
import com.game.plugins.components.scalePivotOffset

/**
 * An extension for `DrawScope` that applies a viewport-adaptive translation and scale.
 * Inside the [block], the coordinate system is transformed into the game's virtual coordinate system.
 *
 * @param transform The ViewportTransform instance containing the scale factor and offset.
 * @param block The drawing logic to be executed within the virtual coordinate system.
 */
inline fun DrawScope.withViewportTransform(
    transform: ViewportTransform,
    block: DrawScope.() -> Unit
) {
    withTransform({
        if (transform.scaleType == ViewportScaleType.Fill) {
            translate(transform.offsetX, transform.offsetY)
        }
        scale(transform.scaleFactor, transform.scaleFactor, pivot = Offset.Zero)
    }) {
        block()
    }
}

/**
 * An extension for `DrawScope` that applies the camera's translation, scale, and rotation.
 * Inside the [block], the coordinate system is transformed into the camera's view space.
 *
 * @param camera The camera component.
 * @param transform The Transform component of the camera entity.
 * @param block The drawing logic to be executed within the camera's coordinate system.
 */
inline fun DrawScope.withCameraTransform(
    camera: Camera,
    transform: Transform,
    block: DrawScope.() -> Unit
) {
    val viewportL = size.width * camera.viewport.left
    val viewportT = size.height * camera.viewport.top
    val viewportW = size.width * camera.viewport.width
    val viewportH = size.height * camera.viewport.height

    val centerX = viewportW / 2f
    val centerY = viewportH / 2f

    withTransform({
        // 1. Clip the drawing area to the camera's viewport.
        clipRect(
            left = viewportL,
            top = viewportT,
            right = viewportL + viewportW,
            bottom = viewportT + viewportH
        )

        // 2. Translate to the center of the viewport.
        translate(centerX, centerY)

        // 3. Apply zoom (scale).
        scale(camera.zoom, camera.zoom, pivot = Offset.Zero)

        // 4. Apply rotation (camera + entity + shake).
        rotate(-(camera.rotation + transform.rotation + camera.shakeRotation))

        // 5. Apply translation (entity position + shake).
        val finalX = transform.position.x + camera.shakeOffset.x
        val finalY = transform.position.y + camera.shakeOffset.y
        translate(-finalX, -finalY)
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
    block: DrawScope.() -> Unit
) {
    val oldSize = drawContext.size
    try {
        val currentSize = transform.size.let { if (it.isSpecified) it else oldSize }
        drawContext.size = currentSize
        withTransform({
            val offsetX = -currentSize.width / 2f
            val offsetY = -currentSize.height / 2f
            translate(transform.position.x + offsetX, transform.position.y + offsetY)
            rotate(transform.rotation, currentSize * transform.rotationPivot)
            scale(transform.scaleX, transform.scaleY, currentSize * transform.scalePivot)
        }) {
            block()
        }
    } finally {
        // Restore the original size of the draw context.
        drawContext.size = oldSize
    }
}

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
    color: Color = Color.Magenta
) {
    val size = transform.size
    if (size.isUnspecified) return

    // This function manually calculates the final world positions of the entity's corners
    // by applying the EXACT same transformation sequence as `withLocalTransform`.

    // 1. Define the 4 corners of the rectangle relative to its top-left corner (0,0).
    val corners = listOf(
        Offset.Zero,                // Top-Left
        Offset(size.width, 0f),     // Top-Right
        Offset(size.width, size.height),// Bottom-Right
        Offset(0f, size.height)     // Bottom-Left
    )

    // 2. Apply the full transformation to each corner to get its final world position.
    val transformedCorners = corners.map { corner ->
        var p = corner

        // Step A: Apply rotation and scale around their respective pivots.
        // This is done in the local space where (0,0) is the top-left.
        // We use the pre-calculated pivot offsets for this.
        p = p.rotate(transform.rotation, transform.rotationPivotOffset)
        p = p.scale(transform.scaleX, transform.scaleY, transform.scalePivotOffset)

        // Step B: Translate the transformed local point to its final world position.
        // This mirrors the `translate(transform.position.x + offsetX, ...)` step.
        val offsetX = -size.width / 2f
        val offsetY = -size.height / 2f
        p += Offset(transform.position.x + offsetX, transform.position.y + offsetY)

        p
    }

    // 3. Draw the lines connecting the transformed corners to form the exact OBB (Oriented Bounding Box).
    for (i in 0 until 4) {
        drawLine(
            color = color,
            start = transformedCorners[i],
            end = transformedCorners[(i + 1) % 4], // Connects back to the start
            strokeWidth = 1f
        )
    }

    // 4. Draw a line from the visual center to the "top" edge to indicate rotation.
    val worldCenter = (transformedCorners[0] + transformedCorners[2]) / 2f
    val topMid = (transformedCorners[0] + transformedCorners[1]) / 2f
    drawLine(
        color = Color.Yellow,
        start = worldCenter,
        end = topMid,
        strokeWidth = 2f
    )
}
