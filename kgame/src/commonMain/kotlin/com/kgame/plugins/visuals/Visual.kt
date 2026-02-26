package com.kgame.plugins.visuals

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope

abstract class Visual {

    constructor()

    constructor(size: Size) {
        this.preferredSize = size
    }

    constructor(size: Float): this(Size(size, size))

    constructor(width: Float, height: Float): this(Size(width, height))

    private var isReady = false

    /**
     * The desired dimensions for this visual.
     * If [Size.Unspecified], the visual should compute its layout based on intrinsic content.
     */
    var preferredSize: Size = Size.Unspecified
        set(value) {
            val changed = field != value
            if (changed) field = value
            if (changed || !isReady) {
                isReady = true
                invalidateBounds()
            }
        }

    private var _bounds: Rect = InfiniteRect

    /**
     * The visual bounding box in local coordinate space.
     * Defaults to [InfiniteRect] to represent unconstrained spatial volume.
     */
    val bounds: Rect get() = _bounds

    /**
     * The resolved pixel dimensions derived from the latest geometry pass.
     * Note: Accessing this when bounds is [InfiniteRect] may yield infinite dimensions.
     */
    val size: Size get() = if (bounds.isInfinite) Size.Unspecified else bounds.size

    open var alpha: Float = 1.0f

    /**
     * Resolves the final layout bounds.
     * Defaults to [InfiniteRect] if [preferredSize] is not specified.
     */
    protected open fun onComputeBounds(): Rect {
        return if (preferredSize.isSpecified) {
            Rect(Offset.Zero, preferredSize)
        } else {
            InfiniteRect
        }
    }

    /**
     * Synchronizes the internal bounds.
     * Forces a re-calculation of all cached spatial properties.
     */
    protected fun invalidateBounds() {
        _bounds = onComputeBounds()
    }

    /**
     * Renders the visual content into the provided [DrawScope].
     */
    abstract fun DrawScope.draw()

    companion object {
        /**
         * A sentinel value representing an unbounded rectangular area.
         */
        protected val InfiniteRect = Rect(
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY
        )
    }
}