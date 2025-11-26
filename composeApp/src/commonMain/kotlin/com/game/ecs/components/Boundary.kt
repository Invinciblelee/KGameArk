package com.game.ecs.components

import com.game.ecs.Component
import com.game.ecs.ComponentType

/**
 * A component of Boundary, used to limit the movement of an entity.
 * @param padding Boundary padding.
 */
data class Boundary(val padding: Float = 50f) : Component<Boundary> {
    override fun type() = Boundary
    companion object : ComponentType<Boundary>()
}