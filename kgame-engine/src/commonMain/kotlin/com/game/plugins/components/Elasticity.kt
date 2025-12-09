package com.game.plugins.components

import com.game.ecs.Component
import com.game.ecs.ComponentType

/**
 * A component of Spring, used to control the spring effect of an entity.
 * @param stiffness Stiffness of the spring.
 * @param damping Damping of the spring.
 */
data class Elasticity(
    val stiffness: Float = 300f,
    val damping: Float = 15f
) : Component<Elasticity> {
    override fun type() = Elasticity

    companion object Companion : ComponentType<Elasticity>()
}