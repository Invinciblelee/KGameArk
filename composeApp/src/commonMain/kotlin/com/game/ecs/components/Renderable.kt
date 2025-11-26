package com.game.ecs.components

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.ecs.Component
import com.game.ecs.ComponentType


/**
 * The visual of renderable
 */
interface Visual {

    /**
     * Visual size. When size == Size.Unspecified, the component is rendered at its intrinsic size.
     */
    val size: Size get() = Size.Unspecified

    /**
     * Drawing logic.
     * Note: RenderSystem usually applies the Matrix transformation beforehand (translation to pos,
     * rotation and scale), so inside draw() you should generally render centered on bounds.center.
     */
    fun DrawScope.draw()

    /**
     * Returns the world-axis aligned bounding box (for frustum culling).
     * Uses transform.pos and transform.scale.
     */
    fun getBounds(transform: Transform, bounds: MutableRect) {
        if (size == Size.Unspecified) {
            bounds.set(
                Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY
            )
            return
        }

        val halfW = (size.width * transform.scaleX) / 2f
        val halfH = (size.height * transform.scaleY) / 2f

        bounds.left = transform.position.x - halfW
        bounds.top = transform.position.y - halfH
        bounds.right = transform.position.x + halfW
        bounds.bottom = transform.position.y + halfH
    }
}

class Circle(val color: Color, radius: Float): Visual {

    override val size: Size = Size(radius * 2, radius * 2)

    override fun DrawScope.draw() {
        drawCircle(color)
    }

}

class Rectangle(val color: Color, override val size: Size = Size.Unspecified): Visual {
    override fun DrawScope.draw() {
        drawRect(color)
    }
}

/**
 * A component of Renderable.
 * @param visual The visual of the renderable.
 * @param zIndex The z-index of the renderable.
 * @param isVisible Whether the renderable is visible.
 */
data class Renderable(
    val visual: Visual,
    val zIndex: Int = 0,
    var isVisible: Boolean = true
) : Component<Renderable>, Comparable<Renderable> {
    override fun type() = Renderable

    companion object : ComponentType<Renderable>()

    override fun compareTo(other: Renderable): Int {
        return zIndex.compareTo(other.zIndex)
    }
}