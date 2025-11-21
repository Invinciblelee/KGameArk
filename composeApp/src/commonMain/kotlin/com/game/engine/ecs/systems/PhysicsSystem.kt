package com.game.engine.ecs.systems

import com.game.engine.ecs.System
import com.game.engine.ecs.components.Rigidbody
import com.game.engine.ecs.components.Transform
import com.game.engine.ecs.components.integrate
import com.game.engine.ecs.each
import com.game.engine.ecs.inject

class PhysicsSystem : System() {

    private val rigidbodyFamily by inject<Transform, Rigidbody>()

    override fun update(deltaTime: Float) {
        rigidbodyFamily.each<Transform, Rigidbody> { _, t, rb ->
            rb.integrate(t, deltaTime)
        }
    }

}