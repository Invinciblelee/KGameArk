package com.game.ecs.injectables

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

enum class ViewportScaleType {
    Fit, Fill
}

interface ViewportTransform {
    var actualSize: Size
    var virtualSize: Size

    var scaleFactor: Float

    var offsetX: Float
    var offsetY: Float

    var scaleType: ViewportScaleType

    fun applyToSize(
        actualSize: Size,
        virtualSize: Size,
        scaleType: ViewportScaleType = ViewportScaleType.Fill
    ) {
        val scaleX = actualSize.width / virtualSize.width
        val scaleY = actualSize.height / virtualSize.height

        scaleFactor = when (scaleType) {
            ViewportScaleType.Fit -> min(scaleX, scaleY)
            ViewportScaleType.Fill -> max(scaleX, scaleY)
        }

        val scaledWidth = virtualSize.width * scaleFactor
        val scaledHeight = virtualSize.height * scaleFactor

        offsetX = (actualSize.width - scaledWidth) / 2f
        offsetY = (actualSize.height - scaledHeight) / 2f

        this.actualSize = actualSize
        this.virtualSize = virtualSize
        this.scaleType = scaleType
    }

    fun actualToVirtual(position: Offset): Offset {
        val xAfterTranslate = position.x - offsetX
        val yAfterTranslate = position.y - offsetY

        val xVirtual = xAfterTranslate / scaleFactor
        val yVirtual = yAfterTranslate / scaleFactor

        return Offset(xVirtual, yVirtual)
    }

    fun virtualToActual(position: Offset): Offset {
        val xAfterScale = position.x * scaleFactor
        val yAfterScale = position.y * scaleFactor

        val xActual = xAfterScale + offsetX
        val yActual = yAfterScale + offsetY

        return Offset(xActual, yActual)
    }

}

/**
 * Calculates the safe boundary area (Rect) for the camera's center point.
 * This is the maximum range the camera center can occupy while ensuring
 * the entire viewport remains inside the world bounds.
 *
 * @param worldBounds The Rect defining the absolute limits of the game map/world.
 * @return A Rect representing the safe zone for the camera center.
 */
fun ViewportTransform.coerceViewportSafeBounds(worldBounds: Rect): Rect {
    if (worldBounds.isInfinite) return worldBounds

    val halfWidth = virtualSize.width / 2f
    val halfHeight = virtualSize.height / 2f

    // Calculate the theoretical safety edges
    val minX = worldBounds.left + halfWidth
    val maxX = worldBounds.right - halfWidth
    val minY = worldBounds.top + halfHeight
    val maxY = worldBounds.bottom - halfHeight

    // If map is smaller than viewport, the safe zone collapses to the center point.
    val centerX = worldBounds.center.x
    val centerY = worldBounds.center.y

    val safeLeft = if (minX <= maxX) minX else centerX
    val safeRight = if (minX <= maxX) maxX else centerX
    val safeTop = if (minY <= maxY) minY else centerY
    val safeBottom = if (minY <= maxY) maxY else centerY

    return Rect(safeLeft, safeTop, safeRight, safeBottom)
}

/**
 * Clamps a raw world position to ensure it resides within the map boundaries,
 * factoring in the viewport size.
 * * Note: This function is primarily used to ensure the camera center (position)
 * does not cause the viewport to leave the map, but it can clamp any arbitrary
 * position within the calculated safe area.
 *
 * @param worldBounds The Rect defining the absolute limits of the game map/world.
 * @param position The raw world position (Offset) to be clamped.
 * @return The clamped position (Offset) that respects the viewable bounds.
 */
fun ViewportTransform.clampPositionInBounds(worldBounds: Rect, position: Offset): Offset {
    // 1. 获取安全区域 (coerceViewportSafeBounds 逻辑不变)
    val safeZone = this.coerceViewportSafeBounds(worldBounds)

    // 2. 将传入的位置钳制在安全区域内。
    val finalX = position.x.coerceIn(safeZone.left, safeZone.right)
    val finalY = position.y.coerceIn(safeZone.top, safeZone.bottom)

    return Offset(finalX, finalY)
}
