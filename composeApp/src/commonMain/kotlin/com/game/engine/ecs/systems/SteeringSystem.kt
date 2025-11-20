package com.game.engine.ecs.systems

import androidx.compose.ui.geometry.Offset
import com.game.engine.ecs.System
import com.game.engine.ecs.components.MoveToTarget
import com.game.engine.ecs.components.Rigidbody
import com.game.engine.ecs.components.SpringFollow
import com.game.engine.ecs.components.Transform
import com.game.engine.ecs.each
import com.game.engine.math.normalize

class SteeringSystem : System() {
    override fun update(dt: Float) {
        // 1. 处理标准移动行为 (Seek / Arrive)
        world.each<Transform, Rigidbody, MoveToTarget> { _, t, rb, move ->
            val diff = move.target - t.position
            val dist = diff.getDistance()
            
            if (dist < move.stopDistance) {
                // 到达目标：清空速度，移除意图(可选)
                rb.velocity = Offset.Zero
                rb.acceleration = Offset.Zero
                return@each
            }

            // 计算期望速度
            var desiredSpeed = move.speed
            
            // Arrive 行为：接近目标时减速
            if (move.arriveEnabled) {
                val slowDownRadius = 100f // 开始减速的范围
                if (dist < slowDownRadius) {
                    desiredSpeed = move.speed * (dist / slowDownRadius)
                }
            }
            
            // 转向力 (Steering Force) = 期望速度 - 当前速度
            // 这样会有惯性，而不是生硬的转弯
            val desiredVel = diff.normalize() * desiredSpeed
            val steerForce = (desiredVel - rb.velocity) * 4f // 4f 是转向灵敏度
            
            rb.addForce(steerForce)
        }

        // 2. 处理弹簧行为 (Spring) - 也就是之前的 Animator 逻辑
        world.each<Transform, Rigidbody, SpringFollow> { _, t, rb, spring ->
            val displacement = t.position - spring.target
            // F_spring = -k * x
            val forceSpring = displacement * -spring.stiffness
            // F_damping = -c * v
            val forceDamping = rb.velocity * -spring.damping
            
            // 施加合力
            rb.addForce(forceSpring + forceDamping)
        }
    }
}