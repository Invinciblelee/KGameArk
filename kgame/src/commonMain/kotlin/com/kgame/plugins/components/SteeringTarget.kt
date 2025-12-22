package com.kgame.plugins.components

import androidx.compose.ui.geometry.Offset
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.ecs.Entity

/**
 * A component of FollowTarget.
 * @param entity The entity to follow.
 */
data class FollowTarget(val entity: Entity) : Component<FollowTarget> {

    override fun type() = FollowTarget

    companion object Companion : ComponentType<FollowTarget>()

}

/**
 * A component of ArriveTarget.
 * @param position The target position to arrive at.
 */
data class ArriveTarget(val position: Offset) : Component<ArriveTarget> {
    override fun type() = ArriveTarget
    companion object Companion : ComponentType<ArriveTarget>()
}