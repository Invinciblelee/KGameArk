package com.kgame.engine.geometry

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

enum class ResolutionScaleType {
    Fit, Fill
}

interface ResolutionManager {
    var actualSize: Size
    var virtualSize: Size
    var scaledSize: Size

    var scaleFactor: Float

    var offsetX: Float
    var offsetY: Float

    var scaleType: ResolutionScaleType

    fun applySize(
        actualSize: Size = this.actualSize,
        virtualSize: Size = this.virtualSize,
        scaleType: ResolutionScaleType = ResolutionScaleType.Fill
    ) {
        this.actualSize = actualSize
        this.virtualSize = virtualSize
        this.scaleType = scaleType

        if (actualSize.width <= 0 || actualSize.height <= 0) return
        if (virtualSize.width <= 0 || virtualSize.height <= 0) return

        val scaleX = actualSize.width / virtualSize.width
        val scaleY = actualSize.height / virtualSize.height

        this.scaleFactor = when (scaleType) {
            ResolutionScaleType.Fit -> min(scaleX, scaleY)
            ResolutionScaleType.Fill -> max(scaleX, scaleY)
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

@Stable
class DefaultResolutionManager: ResolutionManager {
    override var actualSize: Size by mutableStateOf(Size.Zero)
    override var virtualSize: Size by mutableStateOf(Size.Zero)

    override var scaledSize: Size by mutableStateOf(Size.Zero)

    override var scaleFactor: Float by mutableFloatStateOf(1f)
    override var offsetX: Float by mutableFloatStateOf(0f)
    override var offsetY: Float by mutableFloatStateOf(0f)

    override var scaleType: ResolutionScaleType by mutableStateOf(ResolutionScaleType.Fill)
}
