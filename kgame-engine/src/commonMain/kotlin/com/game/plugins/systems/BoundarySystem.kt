package com.game.plugins.systems

import androidx.compose.ui.geometry.Rect
import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.World.Companion.inject
import com.game.plugins.components.Boundary
import com.game.plugins.components.Transform
import com.game.plugins.services.CameraService

/**
 * The BoundarySystem is responsible for detecting when an entity has left the world bounds.
 */
class BoundarySystem(
    private val cameraService: CameraService = inject()
) : IteratingSystem(
    family = family { all(Boundary) }
) {
    private val worldBounds by lazy { cameraService.mainCamera?.worldBounds }

    override fun onTickEntity(entity: Entity) {
        val bounds = worldBounds ?: return

        val transform = entity[Transform]
        val boundary = entity[Boundary]

        if (isOutsideBounds(transform, bounds, boundary.margin)) {
            boundary.onExit(this, entity)
        }
    }

    private fun isOutsideBounds(transform: Transform, bounds: Rect, margin: Float): Boolean {
        val pos = transform.position
        return pos.x < bounds.left - margin ||
                pos.x > bounds.right + margin ||
                pos.y < bounds.top - margin ||
                pos.y > bounds.bottom + margin
    }

}