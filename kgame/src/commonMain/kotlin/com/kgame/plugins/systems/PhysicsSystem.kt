package com.kgame.plugins.systems

import androidx.compose.ui.geometry.Offset
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.plugins.components.RigidBody
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.applyGravity
import com.kgame.plugins.components.integrate

/**
 * The PhysicsSystem is responsible for updating all entities with rigid bodies.
 */
class PhysicsSystem(
    val gravity: Offset = Offset(0f, 980f)
) : IteratingSystem(
    family = family { all(Transform, RigidBody) }
) {

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val transform = entity[Transform]
        val rigidBody = entity[RigidBody]

        rigidBody.applyGravity(gravity)

        rigidBody.integrate(transform, deltaTime)
    }

}