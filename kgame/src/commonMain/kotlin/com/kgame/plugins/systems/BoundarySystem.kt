package com.kgame.plugins.systems

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Rect
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.plugins.components.Boundary
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.WorldBounds
import com.kgame.plugins.services.CameraService

/**
 * The BoundarySystem is responsible for detecting when an entity has left the world bounds.
 */
class BoundarySystem(
    private val cameraService: CameraService = inject(),
) : IteratingSystem(
    family = family { all(Boundary, Transform) }
) {
    private val worldBounds = MutableRect(0f, 0f, 0f, 0f)

    override fun onTick(deltaTime: Float) {
        super.onTick(deltaTime)
    }

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val transform = entity[Transform]
        val boundary = entity[Boundary]

        if (isOutsideBounds(transform, boundary.margin)) {
            boundary.onExit(this, entity)
        }
    }

    private fun isOutsideBounds(transform: Transform, margin: Float): Boolean {
        val pos = transform.position
        return pos.x < worldBounds.left - margin ||
                pos.x > worldBounds.right + margin ||
                pos.y < worldBounds.top - margin ||
                pos.y > worldBounds.bottom + margin
    }

}