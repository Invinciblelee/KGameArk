package com.game.ecs.systems

import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.components.FollowTarget
import com.game.ecs.components.MovementEffect
import com.game.ecs.components.Rigidbody
import com.game.ecs.components.SpringEffect
import com.game.ecs.components.Transform
import com.game.ecs.components.applyMovementForce
import com.game.ecs.components.applySpringForce

class SteeringSystem : IteratingSystem(
    family = family { all(Transform, Rigidbody, FollowTarget).any(MovementEffect, SpringEffect) }
) {

    override fun onTickEntity(entity: Entity) {
        val target = entity[FollowTarget]
        val targetPosition = target.entity[Transform].position

        val rigidbody = entity[Rigidbody]
        val transform = entity[Transform]
        val movementEffect = entity.getOrNull(MovementEffect)
        if (movementEffect != null) {
            rigidbody.applyMovementForce(transform, movementEffect, targetPosition)
        }

        val springEffect = entity.getOrNull(SpringEffect)
        if (springEffect != null) {
            rigidbody.applySpringForce(transform, springEffect, targetPosition)
        }
    }

}