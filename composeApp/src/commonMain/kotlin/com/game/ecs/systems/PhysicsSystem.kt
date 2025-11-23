package com.game.ecs.systems

import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.components.Rigidbody
import com.game.ecs.components.Transform
import com.game.ecs.components.integrate

class PhysicsSystem : IteratingSystem(
    family = family { all(Transform, Rigidbody) }
) {

    override fun onTickEntity(entity: Entity) {
        val transform = entity[Transform]
        val rigidbody = entity[Rigidbody]
        rigidbody.integrate(transform, deltaTime)
    }

}