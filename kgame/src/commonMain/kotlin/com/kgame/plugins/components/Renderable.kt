package com.kgame.plugins.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.util.lerp
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.engine.geometry.Anchor
import com.kgame.plugins.visuals.Visual


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
) : Component<Renderable>, Comparable<Renderable> {
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
     * Returns the size of [visual]
     */
    val size: Size
        get() = visual.size

    /**
     * Returns the bounds of [visual]
     */
    val bounds: Rect
        get() = visual.bounds

    /**
     * Returns the anchor of [visual]
     */
    val anchor: Anchor
        get() = visual.anchor

    /**
     * Returns the alpha of [visual]
     */
    val alpha: Float
        get() = visual.alpha

}


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

/**
 * Applies a new size to the renderable's visual.
 */
fun Renderable.applySize(size: Size) {
    visual.preferredSize = size
}

/**
 * Applies a new size to the renderable's visual.
 */
fun Renderable.applySize(width: Float, height: Float) {
    visual.preferredSize = Size(width, height)
}