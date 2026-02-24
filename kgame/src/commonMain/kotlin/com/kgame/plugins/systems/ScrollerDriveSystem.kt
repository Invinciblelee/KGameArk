package com.kgame.plugins.systems

import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.family
import com.kgame.plugins.components.Axis
import com.kgame.plugins.components.Scroller
import com.kgame.plugins.components.ScrollerTarget
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.Velocity
import kotlin.math.abs

/**
 * High-performance scrolling system based on world distance accumulation.
 * It maps the background position by combining the global cruise distance
 * and the target's relative screen offset.
 */
class ScrollerDriveSystem(priority: SystemPriority = SystemPriorityAnchors.Logic) : IteratingSystem(
    family = family { all(Scroller, Transform) },
    priority = priority
) {
    /** Global progression distance of the world. */
    private var cruiseDistance: Float = 0f

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val scroller = entity[Scroller]
        val transform = entity[Transform]
        val drive = entity.getOrNull(ScrollerTarget)

        // 1. Update world progression based on player's velocity
        // If the target has a Velocity component, use its magnitude to drive the world.
        // Otherwise, fall back to the scroller's base speed.
        val targetVelocity = drive?.entity?.getOrNull(Velocity)
        val currentV = if (targetVelocity != null) {
            if (scroller.axis == Axis.Y) abs(targetVelocity.y) else abs(targetVelocity.x)
        } else {
            scroller.speed
        }

        cruiseDistance += currentV * deltaTime

        // 2. Map target offset based on screen position
        // This creates the parallax/projection effect based on the aircraft's current X/Y.
        var targetOffset = 0f
        drive?.let {
            val targetTransform = it.entity.getOrNull(Transform)
            if (targetTransform != null) {
                val pos = if (scroller.axis == Axis.Y) targetTransform.position.y else targetTransform.position.x
                targetOffset = pos * it.intensity
            }
        }

        // 3. Apply physical projection
        // Final Position = -(Cruise_Distance + Player_Position_Offset)
        // Background moves in the opposite direction of the world progression.
        if (scroller.axis == Axis.Y) {
            transform.y = -(cruiseDistance + targetOffset)
        } else {
            transform.x = -(cruiseDistance + targetOffset)
        }
    }
}