package com.game.engine.ecs.systems

import com.game.engine.ecs.System
import com.game.engine.ecs.components.Lifetime
import com.game.engine.ecs.each
import com.game.engine.ecs.inject

class LifetimeSystem : System() {
    private val lifetimeFamily by inject<Lifetime>()

    override fun update(deltaTime: Float) {
        lifetimeFamily.each<Lifetime> { entity, life ->
            life.age += deltaTime
            
            if (life.age >= life.duration) {
                world.remove(entity)
            }
        }
    }
}