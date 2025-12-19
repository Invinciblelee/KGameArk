package com.kgame.engine.geometry

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.Visual
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Creates an immutable [Rect] by extracting the position from the given [transform]
 * and combining it with the specified [size].
 *
 * @param transform The transformation (position, rotation, etc.) to apply.
 * @param size The dimensions of the rectangle.
 * @return A new immutable [Rect] instance.
 */
fun Rect(transform: Transform, size: Size): Rect {
    return MutableRect(transform, size).toRect()
}

/**
 * Creates a [MutableRect] and initializes its bounds based on the provided [transform]
 * and [size]. The rectangle is typically centered or offset according to the transform's translation.
 *
 * @param transform The source transformation for positioning the rectangle.
 * @param size The width and height to be assigned to the rectangle.
 * @return A initialized [MutableRect] instance that can be further modified.
 */
fun MutableRect(transform: Transform, size: Size): MutableRect {
    return MutableRect(0f, 0f, 0f, 0f).apply { set(transform, size) }
}

/**
 * Calculates the World-Axis Aligned Bounding Box (AABB) for an entity based on its
 * [transform] and original [size], and writes the result to this [MutableRect] receiver.
 *
 * This method correctly accounts for the entity's rotation and scaling to produce an
 * AABB that fully encloses the transformed geometry. It achieves this by transforming the
 * corner points of the local bounding box and finding the new min/max world coordinates.
 *
 * @receiver MutableRect This rectangle is modified in place to hold the resulting World-AABB.
 * @param transform The World Transform component of the target entity (position, rotation, scale).
 * @param size The un-transformed, local size of the target entity's [Visual] or collision volume.
 */
fun MutableRect.set(transform: Transform, size: Size) {
    if (size == Size.Unspecified) {
        set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        return
    }

    val position = transform.position
    val scale = transform.scale

    val halfW = size.width / 2f
    val halfH = size.height / 2f

    // Pre-calculate rotation values
    val angleRad = transform.rotation * (PI / 180f).toFloat()
    val sin = sin(angleRad)
    val cos = cos(angleRad)

    // Convert Pivot coordinates to offsets relative to the center of the object (0, 0)
    // 0.5 (center) -> 0
    // 0.0 (left/top) -> -halfW
    // 1.0 (right/bottom) -> +halfW
    val scalePivotX = (transform.scalePivot.pivotFractionX * size.width) - halfW
    val scalePivotY = (transform.scalePivot.pivotFractionY * size.height) - halfH
    val rotationPivotX = (transform.rotationPivot.pivotFractionX * size.width) - halfW
    val rotationPivotY = (transform.rotationPivot.pivotFractionY * size.height) - halfH

    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    for (i in 0..3) {
        // Define corner relative to center (0, 0)
        val cornerX = if (i and 1 == 0) -halfW else halfW
        val cornerY = if (i < 2) -halfH else halfH

        var px = cornerX
        var py = cornerY

        // 1. Rotate around the rotation pivot
        px -= rotationPivotX
        py -= rotationPivotY
        val rotatedX = px * cos - py * sin
        val rotatedY = px * sin + py * cos
        px = rotatedX
        py = rotatedY
        px += rotationPivotX
        py += rotationPivotY

        // 2. Scale around the scale pivot
        px -= scalePivotX
        py -= scalePivotY
        px *= scale.scaleX
        py *= scale.scaleY
        px += scalePivotX
        py += scalePivotY

        // 3. Translate to the final world position
        // Note: Here we assume that position refers to the center of the object in world coordinates.
        // If position is defined as the top-left corner, further adjustments would be needed.
        // However, based on the definition of cornerX, we typically consider the center here.
        px += position.x
        py += position.y

        minX = min(minX, px)
        minY = min(minY, py)
        maxX = max(maxX, px)
        maxY = max(maxY, py)
    }

    set(minX, minY, maxX, maxY)
}

/**
 * Updates the bounds of this [MutableRect] using a [Offset] as the top-left corner
 * and a [Size] for its dimensions.
 */
fun MutableRect.set(offset: Offset, size: Size) {
    set(offset.x, offset.y, offset.x + size.width, offset.y + size.height)
}

/**
 * Checks if this rectangle overlaps with another rectangular area defined by
 * a top-left [offset] and [size].
 * * Uses the Standard AABB (Axis-Aligned Bounding Box) collision detection.
 */
fun MutableRect.overlaps(offset: Offset, size: Size): Boolean {
    val otherRight = offset.x + size.width
    val otherBottom = offset.y + size.height

    // Standard collision logic:
    // They overlap if they are NOT separated in any direction.
    return this.left < otherRight &&
            this.right > offset.x &&
            this.top < otherBottom &&
            this.bottom > offset.y
}

/**
 * Checks if this rectangle overlaps with another rectangular area defined by
 * [IntOffset] and [IntSize]. Converts integer inputs to floats for comparison.
 */
fun MutableRect.overlaps(offset: IntOffset, size: IntSize): Boolean {
    // Reusing the float version to maintain consistency
    return overlaps(offset = offset.toOffset(), size = size.toSize())
}
