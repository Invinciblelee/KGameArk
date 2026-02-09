package com.kgame.plugins.systems

import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.family
import com.kgame.plugins.components.InvincibleTag

/**
 * System responsible for managing the lifecycle of [InvincibleTag].
 * It decrements the duration and removes the tag when the time expires.
 */
class InvincibleSystem(priority: SystemPriority = SystemPriorityAnchors.Logic) : IteratingSystem(
    family = family { all(InvincibleTag) },
    priority = priority
) {
    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val invincible = entity[InvincibleTag]
        
        // 1. Handle permanent invincibility (duration is null)
        val currentDuration = invincible.duration ?: return

        // 2. Decrement remaining time
        val nextDuration = currentDuration - deltaTime
        invincible.duration = nextDuration

        // 3. Remove the tag once the duration expires
        if (nextDuration <= 0f) {
            // Use configure to perform an atomic structural change
            entity.configure { -InvincibleTag }
        }
    }
}