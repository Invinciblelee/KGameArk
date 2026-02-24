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
    // --- Inputs (Properties) ---
    val actualSize: Size

    val virtualSize: Size

    val scaleType: ResolutionScaleType

    // --- Computed Results ---

    val scaleSize: Size

    val scaleFactor: Float
    val offsetX: Float
    val offsetY: Float

    /**
     * Set the virtual size of the rendering area (the "Canvas").
     */
    fun setVirtualSize(size: Size, scaleType: ResolutionScaleType = this.scaleType)

    /**
     * Set the physical size of the rendering area (the "Box" or "Window").
     */
    fun setActualSize(size: Size)

    /**
     * Set the absolute offset of the rendering area in the root window.
     */
    fun setCanvasOffset(offset: Offset)

    fun actualToVirtual(position: Offset): Offset
    fun virtualToActual(position: Offset): Offset
}

@Stable
class DefaultResolutionManager : ResolutionManager {

    private var _virtualSize by mutableStateOf(Size.Zero)
    override val virtualSize: Size get() = _virtualSize

    private var _actualSize by mutableStateOf(Size.Zero)
    override val actualSize: Size get() = _actualSize
    private var _canvasOffset by mutableStateOf(Offset.Zero)

    private var _scaleType by mutableStateOf(ResolutionScaleType.Fill)
    override val scaleType: ResolutionScaleType get() = _scaleType

    override var scaleSize by mutableStateOf(Size.Zero)
        private set

    override var scaleFactor by mutableFloatStateOf(1f)
        private set

    override var offsetX by mutableFloatStateOf(0f)
        private set
    override var offsetY by mutableFloatStateOf(0f)
        private set

    override fun setVirtualSize(size: Size, scaleType: ResolutionScaleType) {
        if (this._virtualSize == size && this._scaleType == scaleType) return
        this._virtualSize = size
        this._scaleType = scaleType
        layout()
    }

    override fun setActualSize(size: Size) {
        if (this.actualSize == size) return
        this._actualSize = size
        layout()
    }

    override fun setCanvasOffset(offset: Offset) {
        this._canvasOffset = offset
    }

    private fun layout() {
        val aW = actualSize.width
        val aH = actualSize.height
        val vW = virtualSize.width
        val vH = virtualSize.height

        if (aW <= 0f || aH <= 0f || vW <= 0f || vH <= 0f) return

        val scaleX = aW / vW
        val scaleY = aH / vH

        scaleFactor = when (scaleType) {
            ResolutionScaleType.Fit -> min(scaleX, scaleY)
            ResolutionScaleType.Fill -> max(scaleX, scaleY)
        }

        // Calculate the physical size after scaling
        scaleSize = Size(vW * scaleFactor, vH * scaleFactor)

        // Internal offset for centering the game within the actualSize
        offsetX = (aW - (vW * scaleFactor)) / 2f
        offsetY = (aH - (vH * scaleFactor)) / 2f
    }

    override fun actualToVirtual(position: Offset): Offset {
        // 1. Position from Root -> Position relative to the Container (Box)
        val localToBoxX = position.x - _canvasOffset.x
        val localToBoxY = position.y - _canvasOffset.y

        // 2. Position relative to Box -> Position relative to Virtual Space
        return Offset(
            x = (localToBoxX - offsetX) / scaleFactor,
            y = (localToBoxY - offsetY) / scaleFactor
        )
    }

    override fun virtualToActual(position: Offset): Offset {
        return Offset(
            x = (position.x * scaleFactor) + offsetX + _canvasOffset.x,
            y = (position.y * scaleFactor) + offsetY + _canvasOffset.y
        )
    }
}
