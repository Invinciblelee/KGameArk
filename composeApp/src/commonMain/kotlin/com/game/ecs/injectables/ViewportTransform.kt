package com.game.ecs.injectables

import androidx.compose.ui.geometry.Offset
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



