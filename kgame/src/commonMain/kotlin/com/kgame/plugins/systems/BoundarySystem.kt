package com.kgame.plugins.systems

import androidx.compose.ui.geometry.Offset
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.family
import com.kgame.plugins.components.Boundary
import com.kgame.plugins.components.BoundaryStrategy
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CleanupTag
import com.kgame.plugins.components.RigidBody
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.WorldBounds
import com.kgame.plugins.components.applyReflectionImpulse

/**
 * The BoundarySystem is responsible for detecting when an entity has left the world bounds.
 */
class BoundarySystem(priority: SystemPriority = SystemPriorityAnchors.Physics) : IteratingSystem(
    family = family { all(Boundary, Transform).none(Camera) },
    priority = priority
) {

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val worldBounds = entity.getOrNull(WorldBounds) ?: return
        val transform = entity[Transform]
        val boundary = entity[Boundary]

        if (isOutsideBounds(transform, worldBounds, boundary.margin)) {
            handleExit(entity, boundary.strategy, worldBounds, transform)
        }
    }

    private fun handleExit(
        entity: Entity,
        strategy: BoundaryStrategy,
        worldBounds: WorldBounds,
        transform: Transform
    ) {
        when (strategy) {
            BoundaryStrategy.Destroy -> entity.remove()
            BoundaryStrategy.Cleanup -> entity.configure { +CleanupTag }
            BoundaryStrategy.Bounce -> {
                entity.configure {
                   val rigidBody = addIfAbsent(RigidBody) { RigidBody() }

                   handleBounce(transform, worldBounds, rigidBody)
                }
            }
            BoundaryStrategy.Clamp -> handleClamp(transform, worldBounds)
        }
    }

    private fun handleBounce(transform: Transform, worldBounds: WorldBounds, rigidBody: RigidBody) {
        val pos = transform.position
        val rect = worldBounds.rect

        if (pos.x < rect.left) {
            transform.position = pos.copy(x = rect.left)
            rigidBody.applyReflectionImpulse(normal = Offset(1f, 0f), restitution = 0.8f)
        } else if (pos.x > rect.right) {
            transform.position = pos.copy(x = rect.right)
            rigidBody.applyReflectionImpulse(normal = Offset(-1f, 0f), restitution = 0.8f)
        }

        if (pos.y < rect.top) {
            transform.position = pos.copy(y = rect.top)
            rigidBody.applyReflectionImpulse(normal = Offset(0f, 1f), restitution = 0.8f)
        } else if (pos.y > rect.bottom) {
            transform.position = pos.copy(y = rect.bottom)
            rigidBody.applyReflectionImpulse(normal = Offset(0f, -1f), restitution = 0.8f)
        }
    }

    private fun handleClamp(transform: Transform, worldBounds: WorldBounds) {
        val pos = transform.position
        val rect = worldBounds.rect
        transform.position = Offset(
            x = pos.x.coerceIn(rect.left, rect.right),
            y = pos.y.coerceIn(rect.top, rect.bottom)
        )
    }

    private fun isOutsideBounds(transform: Transform, worldBounds: WorldBounds, margin: Float): Boolean {
        val pos = transform.position
        return pos.x < worldBounds.rect.left - margin ||
                pos.x > worldBounds.rect.right + margin ||
                pos.y < worldBounds.rect.top - margin ||
                pos.y > worldBounds.rect.bottom + margin
    }

}