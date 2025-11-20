package com.game.engine.ecs.systems

import androidx.compose.ui.geometry.Offset
import com.game.engine.ecs.System
import com.game.engine.ecs.components.Rigidbody
import com.game.engine.ecs.components.Transform
import com.game.engine.ecs.each
import com.game.engine.math.normalize

class PhysicsSystem : System() {
    override fun update(dt: Float) {
        world.each<Transform, Rigidbody> { _, t, rb ->
            // 1. 应用阻力 (Drag)
            // 使用指数衰减公式，与帧率无关: v = v * e^(-drag * dt)
            // 比 v *= (1 - drag*dt) 更平滑准确
            val dragFactor = kotlin.math.exp(-rb.drag * dt)
            rb.velocity *= dragFactor

            // 2. 速度更新 (v += a * dt)
            rb.velocity += rb.acceleration * dt

            // 3. 限制最大速度 (安全网)
            if (rb.velocity.getDistanceSquared() > rb.maxSpeed * rb.maxSpeed) {
                rb.velocity = rb.velocity.normalize() * rb.maxSpeed
            }

            // 4. 位置更新 (p += v * dt)
            t.position += rb.velocity * dt

            // 5. 重置加速度 (力是瞬时的，每帧需要清零，除非你有持续的重力)
            rb.acceleration = Offset.Zero
        }
    }
}