package com.kgame.plugins.components

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.ecs.Identifiable

/**
 * A component of SpriteAnimation, used to control the animation of a sprite.
 * @param name The name of the animation.
 * @param clip The name of the clip within the animation.
 * @param speed The speed of the animation.
 * @param loop Whether the animation should loop.
 */
data class SpriteAnimation(
    val name: String,
    var clip: String = name,
    var speed: Float = 1f,
    var loop: Boolean = true,
    val autoPlay: Boolean = true
): Component<SpriteAnimation>, Identifiable {
    override val id: Int = Identifiable.nextId()

    override fun type() = SpriteAnimation

    companion object Companion : ComponentType<SpriteAnimation>()
}