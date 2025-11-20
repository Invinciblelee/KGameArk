package com.game.engine.ecs.systems

import com.game.engine.ecs.System
import com.game.engine.ecs.components.Lifetime
import com.game.engine.ecs.each

class LifetimeSystem : System() {
    override fun update(dt: Float) {
        // 遍历所有有寿命的实体
        // 注意：这里需要操作实体本身(移除)，所以用 forEachEntity
        world.each<Lifetime> { entity, life ->
            life.age += dt
            
            if (life.age >= life.duration) {
                world.remove(entity)
            }
        }
    }
}