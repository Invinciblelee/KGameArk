package com.kgame.engine.geometry

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.kgame.plugins.components.Transform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Projects local [rect] into world space using [transform] .
 * @receiver The [MutableRect] to update.
 * @param transform The world transformation state.
 * @param rect The local bounding box (typically 0,0 based).
 */
fun MutableRect.set(
    transform: Transform,
    rect: Rect
) {
    if (rect.isInfinite) {
        set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        return
    }

    if (rect.isEmpty) {
        val pos = transform.position
        set(pos.x, pos.y, pos.x, pos.y)
        return
    }

    val position = transform.position
    val scale = transform.scale
    val rotation = transform.rotation
    val w = rect.width
    val h = rect.height

    // --- 1. Calculate transformation pivots relative to the bounds ---
    val rotPivX = rect.left + transform.rotationPivot.pivotFractionX * w
    val rotPivY = rect.top + transform.rotationPivot.pivotFractionY * h
    val sclPivX = rect.left + transform.scalePivot.pivotFractionX * w
    val sclPivY = rect.top + transform.scalePivot.pivotFractionY * h

    val angleRad = rotation * (PI / 180f).toFloat()
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)

    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    // --- 2. Transform corners to world space ---
    for (i in 0..3) {
        var px = if (i and 1 == 0) rect.left else rect.right
        var py = if (i < 2) rect.top else rect.bottom

        // A. Apply Rotation around the local rotation pivot
        val dxR = px - rotPivX
        val dyR = py - rotPivY
        px = rotPivX + (dxR * cosA - dyR * sinA)
        py = rotPivY + (dxR * sinA + dyR * cosA)

        // B. Apply Scaling around the local scale pivot
        px = sclPivX + (px - sclPivX) * scale.scaleX
        py = sclPivY + (py - sclPivY) * scale.scaleY

        // C. Final Translation
        // Translates the local point to world space by the entity position.
        px += position.x
        py += position.y

        if (px < minX) minX = px
        if (px > maxX) maxX = px
        if (py < minY) minY = py
        if (py > maxY) maxY = py
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
 * Updates the bounds of this [MutableRect] with given [Rect][rect].
 */
fun MutableRect.set(rect: Rect) {
    set(rect.left, rect.top, rect.right, rect.bottom)
}