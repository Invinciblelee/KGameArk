package com.game.plugins.systems

import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.plugins.components.FollowTarget
import com.game.plugins.components.Arriver
import com.game.plugins.components.RigidBody
import com.game.plugins.components.Spring
import com.game.plugins.components.Transform
import com.game.plugins.components.applyArriverForce
import com.game.plugins.components.applySpringForce

class SteeringSystem : IteratingSystem(
    family = family { all(Transform, RigidBody, FollowTarget).any(Arriver, Spring) }
) {

    override fun onTickEntity(entity: Entity) {
        val target = entity[FollowTarget]
        val targetPosition = target.entity[Transform].position

        val rigidBody = entity[RigidBody]
        val transform = entity[Transform]
        val arriver = entity.getOrNull(Arriver)
        if (arriver != null) {
            rigidBody.applyArriverForce(transform, arriver, targetPosition)
        }

        val spring = entity.getOrNull(Spring)
        if (spring != null) {
            rigidBody.applySpringForce(transform, spring, targetPosition)
        }
    }

}