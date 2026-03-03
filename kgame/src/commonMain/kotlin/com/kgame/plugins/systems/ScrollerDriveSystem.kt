package com.kgame.plugins.systems

import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.family
import com.kgame.plugins.components.ScrollerAxis
import com.kgame.plugins.components.Scroller
import com.kgame.plugins.components.ScrollerTarget
import com.kgame.plugins.components.Transform

/**
 * High-performance scrolling system based on world distance accumulation.
 * It maps the background position by combining the global cruise distance
 * and the target's relative screen offset.
 */
class ScrollerDriveSystem(priority: SystemPriority = SystemPriorityAnchors.Logic) : IteratingSystem(
    family = family { all(Scroller, Transform) },
    priority = priority
) {
    /** Global progression distance. */
    private var cruiseDistance: Float = 0f

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val scroller = entity[Scroller]
        val transform = entity[Transform]
        val drive = entity.getOrNull(ScrollerTarget)

        // 1. World Cruise Progression
        // Keep this independent of player movement to ensure
        // enemies don't "stick" to the background.
        cruiseDistance += scroller.speed * deltaTime

        // 2. The "Subtle" Compensation (Visual Tilt)
        // To fix the "sluggish" feel without breaking physics:
        // Instead of raw position, we use a very small intensity
        // or only use it for a "camera shake/tilt" effect.
        var targetOffset = 0f
        drive?.let { target ->
            val targetTransform = target.entity[Transform]
            // We use targetTransform.position.y but keep intensity VERY LOW (e.g. 0.05)
            targetOffset = targetTransform.position.y * target.intensity
        }

        // 3. Final Coordinate Mapping
        // Use -(cruiseDistance + targetOffset)
        // This maintains the base flow but adds a small 'push' when moving.
        if (scroller.axis == ScrollerAxis.Y) {
            transform.y = -(cruiseDistance + targetOffset)
        } else {
            transform.x = -(cruiseDistance + targetOffset)
        }
    }
}