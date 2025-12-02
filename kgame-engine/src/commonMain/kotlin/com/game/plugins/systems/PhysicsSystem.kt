package com.game.plugins.systems

import androidx.compose.ui.geometry.Offset
import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.plugins.components.RigidBody
import com.game.plugins.components.Transform
import com.game.plugins.components.applyGravity
import com.game.plugins.components.integrate

/**
 * The PhysicsSystem is responsible for updating all entities with rigid bodies.
 */
class PhysicsSystem(
    val gravity: Offset = Offset(0f, 980f)
) : IteratingSystem(
    family = family { all(Transform, RigidBody) }
) {

    override fun onTickEntity(entity: Entity) {
        val transform = entity[Transform]
        val rigidBody = entity[RigidBody]

        rigidBody.applyGravity(gravity)

        rigidBody.integrate(transform, deltaTime)
    }

}