package com.game.plugins.systems

import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.plugins.components.CleanupTag
import com.game.plugins.components.CharacterStats
import com.game.plugins.components.isAlive

class CleanupSystem: IteratingSystem(
    family = family { any(CleanupTag, CharacterStats) }
) {

    override fun onTickEntity(entity: Entity) {
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