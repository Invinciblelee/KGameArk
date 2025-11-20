package com.game.engine.ecs.systems

import com.game.engine.ecs.System
import com.game.engine.ecs.components.Boundary
import com.game.engine.ecs.components.Transform
import com.game.engine.ecs.each

class BoundarySystem : System() {
    override fun update(dt: Float) {
        // 获取屏幕尺寸
        val screenSize = world.size
        
        world.each<Transform, Boundary> { entity, t, b ->
            val pos = t.position
            val padding = b.padding
            
            // 简单的 AABB 检查
            if (pos.x < -padding || pos.x > screenSize.width + padding ||
                pos.y < -padding || pos.y > screenSize.height + padding) {
                world.remove(entity)
            }
        }
    }
}