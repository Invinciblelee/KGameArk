package com.game.plugins.components

import androidx.compose.ui.geometry.CornerRadius
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
     * Drawing logic.
     * Note: RenderSystem usually applies the Matrix transformation beforehand (translation to pos,
     * rotation and scale), so inside draw() you should generally render centered on bounds.center.
     */
    fun DrawScope.draw()

}

class Circle(val color: Color) : Visual {

    override fun DrawScope.draw() {
        drawCircle(color)
    }

}

class Rectangle(
    val color: Color,
    val cornerRadius: CornerRadius = CornerRadius.Zero
) : Visual {
    override fun DrawScope.draw() {
        if (cornerRadius.isZero()) {
            drawRect(color)
        } else {
            drawRoundRect(color, cornerRadius = cornerRadius)
        }
    }
}

/**
 * A component of Renderable.
 * @param visual The visual of the renderable.
 * @param zIndex The z-index of the renderable.
 * @param alpha The alpha of the renderable.
 * @param isVisible Whether the renderable is visible.
 */
data class Renderable(
    val visual: Visual,
    val zIndex: Int = 0,
    var alpha: Float = 1f,
    var isVisible: Boolean = true
) : Component<Renderable>, Comparable<Renderable> {
    override fun type() = Renderable

    companion object : ComponentType<Renderable>()

    override fun compareTo(other: Renderable): Int {
        return zIndex.compareTo(other.zIndex)
    }
}

val Renderable.isShowing: Boolean
    get() = isVisible && alpha > 0f