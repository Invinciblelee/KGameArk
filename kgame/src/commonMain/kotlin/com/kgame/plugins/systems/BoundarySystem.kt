package com.kgame.plugins.systems

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.geometry.ResolutionManager
import com.kgame.plugins.components.Boundary
import com.kgame.plugins.components.BoundaryStrategy
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CleanupTag
import com.kgame.plugins.components.GlobalTag
import com.kgame.plugins.components.RigidBody
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.WorldBounds
import com.kgame.plugins.components.applyReflectionImpulse
import kotlin.math.max
import kotlin.math.min

/**
 * The BoundarySystem is responsible for detecting when an entity has left the world bounds.
 */
class BoundarySystem(
    private val resolution: ResolutionManager = inject(),
    priority: SystemPriority = SystemPriorityAnchors.Physics
) : IteratingSystem(
    family = family { all(Boundary, Transform).none(Camera) },
    priority = priority
) {
    private val globalBoundsFamily = family { all(WorldBounds, GlobalTag) }
    private val worldRect = MutableRect(0f, 0f, 0f, 0f)

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val worldBounds = entity.getOrNull(WorldBounds)
            ?: globalBoundsFamily.firstOrNull()?.get(WorldBounds)
            ?: return
        val transform = entity[Transform]
        val boundary = entity[Boundary]

        computeEffectiveBounds(worldBounds)

        if (isOutsideBounds(transform, boundary.margin)) {
            handleExit(entity, boundary, transform)
        }
    }

    private fun computeEffectiveBounds(worldBounds: WorldBounds) {
        val rect = worldBounds.rect
        val vOffsetX = resolution.offsetX / resolution.scaleFactor
        val vOffsetY = resolution.offsetY / resolution.scaleFactor

        worldRect.left = max(rect.left, rect.left - vOffsetX)
        worldRect.right = min(rect.right, rect.right + vOffsetX)
        worldRect.top = max(rect.top, rect.top - vOffsetY)
        worldRect.bottom = min(rect.bottom, rect.bottom + vOffsetY)
    }

    private fun handleExit(
        entity: Entity,
        boundary: Boundary,
        transform: Transform
    ) {
        when (boundary.strategy) {
            BoundaryStrategy.Destroy -> entity.remove()
            BoundaryStrategy.Cleanup -> entity.configure { +CleanupTag }
            BoundaryStrategy.Bounce -> {
                entity.configure {
                   val rigidBody = addIfAbsent(RigidBody) { RigidBody() }

                   handleBounce(transform, rigidBody)
                }
            }
            BoundaryStrategy.Clamp -> handleClamp(transform, boundary.margin)
        }
    }

    private fun handleBounce(transform: Transform, rigidBody: RigidBody) {
        val pos = transform.position

        if (pos.x < worldRect.left) {
            transform.position = pos.copy(x = worldRect.left)
            rigidBody.applyReflectionImpulse(normal = Offset(1f, 0f), restitution = 0.8f)
        } else if (pos.x > worldRect.right) {
            transform.position = pos.copy(x = worldRect.right)
            rigidBody.applyReflectionImpulse(normal = Offset(-1f, 0f), restitution = 0.8f)
        }

        if (pos.y < worldRect.top) {
            transform.position = pos.copy(y = worldRect.top)
            rigidBody.applyReflectionImpulse(normal = Offset(0f, 1f), restitution = 0.8f)
        } else if (pos.y > worldRect.bottom) {
            transform.position = pos.copy(y = worldRect.bottom)
            rigidBody.applyReflectionImpulse(normal = Offset(0f, -1f), restitution = 0.8f)
        }
    }

    private fun handleClamp(transform: Transform, margin: Float) {
        val pos = transform.position
        transform.position = Offset(
            x = pos.x.coerceIn(worldRect.left - margin, worldRect.right + margin),
            y = pos.y.coerceIn(worldRect.top - margin, worldRect.bottom + margin)
        )
    }

    private fun isOutsideBounds(transform: Transform,  margin: Float): Boolean {
        val pos = transform.position
        return pos.x < worldRect.left - margin ||
                pos.x > worldRect.right + margin ||
                pos.y < worldRect.top - margin ||
                pos.y > worldRect.bottom + margin
    }

}