package com.game.plugins.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import com.game.ecs.Component
import com.game.ecs.ComponentType
import com.game.engine.image.AtlasRegion
import com.game.engine.image.ImageAtlas


/**
 * The visual of renderable
 */
abstract class Visual {

    var alpha: Float = 1f

    /**
     * Drawing logic.
     * Note: RenderSystem usually applies the Matrix transformation beforehand (translation to pos,
     * rotation and scale), so inside draw() you should generally render centered on bounds.center.
     */
    abstract fun DrawScope.draw()

}

class Circle(val color: Color) : Visual() {

    override fun DrawScope.draw() {
        drawCircle(color, alpha = alpha)
    }

}

class Rectangle(
    val color: Color,
    val cornerRadius: CornerRadius = CornerRadius.Zero
) : Visual() {
    override fun DrawScope.draw() {
        if (cornerRadius.isZero()) {
            drawRect(color, alpha = alpha)
        } else {
            drawRoundRect(color, cornerRadius = cornerRadius, alpha = alpha)
        }
    }
}

class Image(
    val bitmap: ImageBitmap
): Visual() {

    override fun DrawScope.draw() {
        drawImage(
            image = bitmap,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            alpha = alpha
        )
    }
}

class Sprite(
    val atlas: ImageAtlas,
    name: String,
) : Visual() {

    var name: String = name
        private set
    
    private var region: AtlasRegion = atlas.getRegion(name)

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
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
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
    var visual: Visual,
    var zIndex: Int = 0,
    var isVisible: Boolean = true
): Component<Renderable>, Comparable<Renderable> {
    override fun type() = Renderable
    
    companion object : ComponentType<Renderable>()

    override fun compareTo(other: Renderable): Int {
        return zIndex.compareTo(other.zIndex)
    }
}

/**
 * Checks if the renderable is visible and has an alpha greater than 0.
 */
val Renderable.isShowing: Boolean
    get() = isVisible && visual.alpha > 0f

/**
 * Checks if the renderable is not visible or has an alpha less than or equal to 0.
 */
val Renderable.isHiding: Boolean
    get() = !isVisible || visual.alpha <= 0f

/**
 * Applies a new alpha to the renderable's visual.
 * @param alpha The new alpha value.
 */
fun Renderable.applyAlpha(alpha: Float) {
    visual.alpha = alpha
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
    visual.alpha = lerp(fromAlpha, toAlpha, fraction)
}