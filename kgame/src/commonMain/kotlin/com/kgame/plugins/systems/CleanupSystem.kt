package com.kgame.plugins.systems

import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.plugins.components.CleanupTag
import com.kgame.plugins.components.CharacterStats
import com.kgame.plugins.components.isAlive

class CleanupSystem: IteratingSystem(
    family = family { any(CleanupTag, CharacterStats) }
) {

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        if (entity.has(CleanupTag)) {
            entity.remove()
            return
        }

        val characterStats = entity.getOrNull(CharacterStats)
        if (characterStats?.isAlive == false) {
            entity.remove()
        }
    }

}