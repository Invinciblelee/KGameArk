package com.game.plugins.components

import com.game.ecs.Component
import com.game.ecs.ComponentType
import com.game.ecs.Entity
import com.game.ecs.EntityComponentContext

/**
 * A component that represents a boundary for an entity.
 * @property margin The margin to use for the boundary.
 * @property onExit A function to call when the entity exits the boundary.
 */
data class Boundary(
    val margin: Float = 100f,
    val onExit: EntityComponentContext.(entity: Entity) -> Unit = destroyOnExit()
) : Component<Boundary> {
    override fun type() = Boundary
    companion object : ComponentType<Boundary>()
}

fun cleanupOnExit(): EntityComponentContext.(Entity) -> Unit = { entity ->
    entity.configure { +CleanupTag }
}

fun destroyOnExit(): EntityComponentContext.(Entity) -> Unit = { entity ->
    entity.remove()
}