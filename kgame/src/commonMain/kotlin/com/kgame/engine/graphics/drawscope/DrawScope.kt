package com.kgame.engine.graphics.drawscope

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.kgame.engine.geometry.Anchor
import com.kgame.engine.geometry.ResolutionManager
import com.kgame.engine.geometry.ResolutionScaleType
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

    val finalCameraX = (cameraPos.x + shakeOffsetX).toInt().toFloat()
    val finalCameraY = (cameraPos.y + shakeOffsetY).toInt().toFloat()

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
 * @param size The size of the entity's bounding box in the world coordinate system.
 * @param anchor The anchor point of the entity's bounding box.
 * @param block The entity's local drawing logic (e.g., drawCircle(Color.Red)).
 */
inline fun DrawScope.withLocalTransform(
    transform: Transform,
    size: Size,
    anchor: Anchor,
    block: DrawScope.() -> Unit
) {
    val oldSize = drawContext.size
    try {
        val currentSize = if (size.isSpecified) size else oldSize
        drawContext.size = currentSize

        val halfW = currentSize.width / 2f
        val halfH = currentSize.height / 2f

        val position = transform.position
        val rotation = transform.rotation
        val scale = transform.scale

        withTransform({
            // The pivot represents the offset from the top-left corner (0, 0) to the specified anchor.
            // When translating, we subtract this pivot from the world position to ensure (0, 0)
            // in the local block aligns with the entity's top-left corner.
            val pivotX = when (anchor) {
                Anchor.TopLeft, Anchor.CenterLeft, Anchor.BottomLeft -> 0f
                Anchor.TopCenter, Anchor.Center, Anchor.BottomCenter -> halfW
                Anchor.TopRight, Anchor.CenterRight, Anchor.BottomRight -> currentSize.width
            }
            val pivotY = when (anchor) {
                Anchor.TopLeft, Anchor.TopCenter, Anchor.TopRight -> 0f
                Anchor.CenterLeft, Anchor.Center, Anchor.CenterRight -> halfH
                Anchor.BottomLeft, Anchor.BottomCenter, Anchor.BottomRight -> currentSize.height
            }

            // Translate to the world position and subtract the pivot to reach the local top-left.
            // E.g., if anchor is Center, we translate to (position.x - halfW, position.y - halfH).
            translate(position.x - pivotX, position.y - pivotY)

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

expect fun DrawScope.drawVertices(
    vertexMode: VertexMode,
    positions: FloatArray,
    colors: IntArray? = null,
    texCoords: FloatArray? = null,
    indices: ShortArray? = null,
    blendMode: BlendMode,
    paint: Paint
)