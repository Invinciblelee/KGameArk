package com.game.engine.ecs.systems

import com.game.engine.ecs.System
import com.game.engine.ecs.components.Transform
import com.game.engine.ecs.components.Velocity
import com.game.engine.ecs.each

class MovementSystem : System() {
    override fun update(dt: Float) {
        // 自动处理所有带位置和速度的实体
        world.each<Transform, Velocity> { _, t, v ->
            // 1. 位移
            t.position += v.vector * dt
            
            // 2. 旋转 (如果需要速度驱动旋转，可扩展 AngularVelocity)
            
            // 3. 阻力/摩擦力 (模拟惯性停车)
            if (v.drag > 0) {
                // 简单的线性衰减
                v.vector *= (1f - v.drag * dt).coerceIn(0f, 1f)
            }
        }
    }
}