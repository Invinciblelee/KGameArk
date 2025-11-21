package com.game.engine.ecs.systems

import com.game.engine.ecs.System
import com.game.engine.ecs.components.Movement
import com.game.engine.ecs.components.Rigidbody
import com.game.engine.ecs.components.SpringFollow
import com.game.engine.ecs.components.Transform
import com.game.engine.ecs.components.applyMovement
import com.game.engine.ecs.components.applySpringFollow
import com.game.engine.ecs.each
import com.game.engine.ecs.inject

class SteeringSystem : System() {

    private val movementFamily by inject<Transform, Rigidbody, Movement>()

    private val followFamily by inject<Transform, Rigidbody, SpringFollow>()

    override fun update(deltaTime: Float) {
        // 1. 处理标准移动行为 (Seek / Arrive)
        movementFamily.each<Transform, Rigidbody, Movement> { _, t, rb, move ->
            rb.applyMovement(t, move) // 极简调用
        }

        // 2. 处理弹簧行为 (Spring)
        followFamily.each<Transform, Rigidbody, SpringFollow> { _, t, rb, spring ->
            rb.applySpringFollow(t, spring) // 极简调用
        }
    }
}