package com.game.engine.geometry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    var scaledSize: Size

    var scaleFactor: Float

    var offsetX: Float
    var offsetY: Float

    var scaleType: ViewportScaleType

    fun applyToSize(
        actualSize: Size = this.actualSize,
        virtualSize: Size = this.virtualSize,
        scaleType: ViewportScaleType = ViewportScaleType.Fill
    ) {
        this.actualSize = actualSize
        this.virtualSize = virtualSize
        this.scaleType = scaleType

        if (actualSize.width <= 0 || actualSize.height <= 0) return
        if (virtualSize.width <= 0 || virtualSize.height <= 0) return

        val scaleX = actualSize.width / virtualSize.width
        val scaleY = actualSize.height / virtualSize.height

        this.scaleFactor = when (scaleType) {
            ViewportScaleType.Fit -> min(scaleX, scaleY)
            ViewportScaleType.Fill -> max(scaleX, scaleY)
        }

        val scaledWidth = virtualSize.width * scaleFactor
        val scaledHeight = virtualSize.height * scaleFactor

        this.scaledSize = Size(scaledWidth, scaledHeight)

        this.offsetX = (actualSize.width - scaledWidth) / 2f
        this.offsetY = (actualSize.height - scaledHeight) / 2f
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

class DefaultViewportTransform: ViewportTransform {
    override var actualSize: Size by mutableStateOf(Size.Zero)
    override var virtualSize: Size by mutableStateOf(Size.Zero)

    override var scaledSize: Size by mutableStateOf(Size.Zero)

    override var scaleFactor: Float by mutableFloatStateOf(1f)
    override var offsetX: Float by mutableFloatStateOf(0f)
    override var offsetY: Float by mutableFloatStateOf(0f)

    override var scaleType: ViewportScaleType by mutableStateOf(ViewportScaleType.Fill)
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
