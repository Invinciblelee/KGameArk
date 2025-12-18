package com.kgame.plugins.components

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.engine.graphics.atlas.ImageAtlas

/**
 * A component of SpriteAnimation, used to control the animation of a sprite.
 * @param name The name of the animation sequence.
 * @param speed The speed of the animation.
 * @param loop Whether the animation should loop.
 * @param autoPlay Whether the animation should start automatically.
 */
data class SpriteAnimation(
    var name: String,
    var speed: Float = 1f,
    var loop: Boolean = true,
    val autoPlay: Boolean = true
): Component<SpriteAnimation>, Identifiable {
    override val id: Int = Identifiable.nextId()

    override fun type() = SpriteAnimation

    companion object Companion : ComponentType<SpriteAnimation>()
}