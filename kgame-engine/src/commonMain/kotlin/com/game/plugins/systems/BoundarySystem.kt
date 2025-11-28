package com.game.plugins.systems

import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.World.Companion.inject
import com.game.plugins.components.Boundary
import com.game.plugins.components.Transform
import com.game.engine.geometry.ViewportTransform

class BoundarySystem(
    private val viewportTransform: ViewportTransform = inject()
) : IteratingSystem(
    family = family { all(Transform, Boundary) }
) {

    override fun onTickEntity(entity: Entity) {
        val actualSize = viewportTransform.actualSize
        val transform = entity[Transform]
        val boundary = entity[Boundary]

        val pos = transform.position
        val padding = boundary.padding

        if (pos.x < -padding || pos.x > actualSize.width + padding ||
            pos.y < -padding || pos.y > actualSize.height + padding
        ) {
            entity.remove()
        }
    }
}