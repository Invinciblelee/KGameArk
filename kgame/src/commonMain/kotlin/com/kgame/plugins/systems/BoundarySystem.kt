package com.kgame.plugins.systems

import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.plugins.components.Boundary
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.WorldBounds

/**
 * The BoundarySystem is responsible for detecting when an entity has left the world bounds.
 */
class BoundarySystem() : IteratingSystem(
    family = family { all(Boundary, Transform) }
) {


    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        if (entity has Camera) return
        val worldBounds = entity.getOrNull(WorldBounds) ?: return
        val transform = entity[Transform]
        val boundary = entity[Boundary]

        if (isOutsideBounds(transform, worldBounds, boundary.margin)) {
            boundary.onExit(this, entity)
        }
    }

    private fun isOutsideBounds(transform: Transform, worldBounds: WorldBounds, margin: Float): Boolean {
        val pos = transform.position
        return pos.x < worldBounds.rect.left - margin ||
                pos.x > worldBounds.rect.right + margin ||
                pos.y < worldBounds.rect.top - margin ||
                pos.y > worldBounds.rect.bottom + margin
    }

}