package com.game.plugins.systems

import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.plugins.components.RigidBody
import com.game.plugins.components.Transform
import com.game.plugins.components.integrate

class PhysicsSystem : IteratingSystem(
    family = family { all(Transform, RigidBody) }
) {

    override fun onTickEntity(entity: Entity) {
        val transform = entity[Transform]
        val rigidBody = entity[RigidBody]
        rigidBody.integrate(transform, deltaTime)
    }

}