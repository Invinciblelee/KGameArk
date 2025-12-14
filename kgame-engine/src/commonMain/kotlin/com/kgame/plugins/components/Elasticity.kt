package com.kgame.plugins.components

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType

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