package com.game.engine.ecs.systems

import com.game.engine.ecs.System
import com.game.engine.ecs.components.Tween
import com.game.engine.ecs.each
import kotlin.math.abs

class TweenSystem : System() {
    override fun update(dt: Float) {
        world.each<Tween> { entity, tween ->
            if (abs(tween.current - tween.target) > 0.001f) {
                // 简单的线性插值 (Lerp)
                // 或者你可以改成 moveToward 匀速运动
                val diff = tween.target - tween.current
                tween.current += diff * tween.speed * dt
            } else {
                // 到达目标
                tween.current = tween.target
                tween.onComplete?.invoke()
                entity.remove<Tween>() // 自动移除，节省性能
            }
        }
    }
}