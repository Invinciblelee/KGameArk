package com.game.plugins.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.game.ecs.Component
import com.game.ecs.ComponentType
import com.game.engine.image.AtlasRegion
import com.game.engine.image.ImageAtlas


/**
 * The visual of renderable
 */
abstract class Visual(size: Size = Size.Unspecified) {

    constructor(size: Float): this(Size(size, size))

    constructor(width: Float, height: Float): this(Size(width, height))

    private var _size: Size = size
    open val size: Size get() = _size

    private var _alpha: Float = 1f
    open val alpha: Float get() = _alpha

    val isSizeSpecified: Boolean get() = _size.isSpecified

    fun setSize(size: Size) {
        this._size = size
    }

    fun setAlpha(alpha: Float) {
        this._alpha = alpha
    }

    /**
     * Drawing logic.
     * Note: RenderSystem usually applies the Matrix transformation beforehand (translation to pos,
     * rotation and scale), so inside draw() you should generally render centered on bounds.center.
     */
    abstract fun DrawScope.draw()

}

class Circle(val color: Color, size: Float = Float.NaN) : Visual(size) {

    override fun DrawScope.draw() {
        drawCircle(color, alpha = alpha)
    }

}

class Rectangle(
    val color: Color,
    val cornerRadius: CornerRadius = CornerRadius.Zero,
    size: Size = Size.Unspecified
) : Visual(size) {
    override fun DrawScope.draw() {
        if (cornerRadius.isZero()) {
            drawRect(color, alpha = alpha)
        } else {
            drawRoundRect(color, cornerRadius = cornerRadius, alpha = alpha)
        }
    }
}

class Texture(
    val bitmap: ImageBitmap,
    size: Size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
): Visual(size) {
    override fun DrawScope.draw() {
        drawImage(
            image = bitmap,
            dstSize = if (isSizeSpecified) {
                size.toIntSize()
            } else {
                IntSize(bitmap.width, bitmap.height)
            },
            alpha = alpha
        )
    }
}

class Sprite(
    val atlas: ImageAtlas,
    name: String,
    size: Size = Size.Unspecified
) : Visual(size) {

    var name: String = name
        private set

    private var region: AtlasRegion = atlas.getRegion(name)

    override val size: Size
        get() {
            val superSize = super.size
            return if (superSize.isSpecified) {
                superSize
            } else {
                region.size.toSize()
            }
        }

    fun setFrame(name: String) {
        if (this.name == name) return
        this.name = name
        this.region = atlas.getRegion(name)
    }
    
    override fun DrawScope.draw() {
        drawImage(
            image = atlas.bitmap,
            srcOffset = region.offset,
            srcSize = region.size,
            dstOffset = IntOffset.Zero,
            dstSize = if (isSizeSpecified) {
                size.toIntSize()
            } else {
                region.size
            },
            alpha = alpha
        )
    }
}

/**
 * A component of Sprite.
 * @param visual The visual of the sprite.
 * @param zIndex The z-index of the sprite.
 * @param isVisible Whether the sprite is visible.
 */
data class Renderable(
    val visual: Visual,
    var zIndex: Int = 0,
    var isVisible: Boolean = true
): Component<Renderable>, Comparable<Renderable> {
    override fun type() = Renderable
    
    companion object : ComponentType<Renderable>()

    override fun compareTo(other: Renderable): Int {
        return zIndex.compareTo(other.zIndex)
    }


    /**
     * Checks if the renderable is visible and has an alpha greater than 0.
     */
    val isShowing: Boolean
        get() = isVisible && visual.alpha > 0f

    /**
     * Checks if the renderable is not visible or has an alpha less than or equal to 0.
     */
    val isHiding: Boolean
        get() = !isVisible || visual.alpha <= 0f


    /**
     * Returns the size of visual
     */
    val size: Size
        get() = visual.size

    /**
     * Returns the alpha of visual
     */
    val alpha: Float
        get() = visual.alpha

}



/**
 * Applies a new alpha to the renderable's visual.
 * @param alpha The new alpha value.
 */
fun Renderable.applyAlpha(alpha: Float) {
    visual.setAlpha(alpha)
}

/**
 * Applies a new alpha to the renderable's visual.
 * @param fromAlpha The starting alpha value.
 * @param toAlpha The ending alpha value.
 * @param fraction The fraction of the transition.
 */
fun Renderable.applyAlpha(
    fromAlpha: Float,
    toAlpha: Float,
    fraction: Float
) {
    visual.setAlpha(lerp(fromAlpha, toAlpha, fraction))
}

/**
 * Applies a new size to the renderable's visual.
 */
fun Renderable.applySize(size: Size) {
    visual.setSize(size)
}

/**
 * Applies a new size to the renderable's visual.
 */
fun Renderable.applySize(width: Float, height: Float) {
    visual.setSize(Size(width, height))
}