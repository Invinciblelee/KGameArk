package com.game.engine.geometry

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import com.game.plugins.components.Transform
import com.game.plugins.components.Visual
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

fun Rect(transform: Transform, size: Size): Rect {
    return MutableRect(transform, size).toRect()
}

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