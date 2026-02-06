package com.kgame.plugins.components

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.ecs.Entity
import com.kgame.ecs.EntityComponentContext

/**
 * A component that represents a boundary for an entity.
 * @property margin The margin to use for the boundary.
 * @property strategy The strategy to use when the entity exits the boundary.
 */
data class Boundary(
    val margin: Float = 100f,
    val strategy: BoundaryStrategy = BoundaryStrategy.Destroy
) : Component<Boundary> {
    override fun type() = Boundary
    companion object : ComponentType<Boundary>()
}

enum class BoundaryStrategy {
    Destroy,
    Cleanup,
    Bounce,
    Clamp
}