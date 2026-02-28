package com.kgame.plugins.systems

import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.family
import com.kgame.plugins.components.* // Import all components

/**
 * The SteeringSystem is responsible for applying forces to entities to create
 * autonomous movement behaviors like arriving, following, or wandering.
 */
class SteeringSystem(priority: SystemPriority = SystemPriorityAnchors.Logic) : IteratingSystem(
    family = family {
        all(Transform, RigidBody)
        // An entity can have any of these behaviors
        any(Wander, Arriver, Elasticity, ArriveTarget, FollowTarget)
    },
    priority = priority
) {
    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val transform = entity[Transform]
        val rigidBody = entity[RigidBody]

        // 1. Check for one-shot "ArriveTarget" command first.
        val arriveTarget = entity.getOrNull(ArriveTarget)
        if (arriveTarget != null && arriveTarget.enabled) {
            val distSq = transform.distanceSquaredTo(arriveTarget.position)
            val stopSq = arriveTarget.stopDistance * arriveTarget.stopDistance
            // Check if we have arrived (e.g., within a small threshold)
            if (distSq <= stopSq) { // 10 pixels is our arrival threshold
                arriveTarget.enabled = false
            } else {
                // We still need an Arriver component to define *how* to arrive.
                entity.getOrNull(Arriver)?.let { arriver ->
                    rigidBody.applyArriverForce(transform, arriver, arriveTarget.position)
                }
            }
            return
        }

        // 2. Check for persistent "FollowTarget" behavior.
        val followTarget = entity.getOrNull(FollowTarget)
        if (followTarget != null && followTarget.enabled) {
            val targetPosition = followTarget.actor[Transform].position
            val distSq = transform.distanceSquaredTo(targetPosition)
            val minSq = followTarget.minDistance * followTarget.minDistance

            if (distSq <= minSq) {
                followTarget.enabled = false
                return
            }

            entity.getOrNull(Arriver)?.let { arriver ->
                rigidBody.applyArriverForce(transform, arriver, targetPosition)
                return
            }
            entity.getOrNull(Elasticity)?.let { elasticity ->
                rigidBody.applyElasticityForce(transform, elasticity, targetPosition)
                return
            }
        }

        // 3. If no other behaviors are active, wander.
        entity.getOrNull(Wander)?.let { wander ->
            rigidBody.applyWanderForce(wander)
        }
    }
}
