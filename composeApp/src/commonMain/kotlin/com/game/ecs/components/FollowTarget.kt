package com.game.ecs.components

import com.game.ecs.Component
import com.game.ecs.ComponentType
import com.game.ecs.Entity

/**
 * A component of FollowTarget.
 * @param entity The entity to follow.
 */
data class FollowTarget(val entity: Entity) : Component<FollowTarget> {

    override fun type() = FollowTarget

    companion object : ComponentType<FollowTarget>()

}