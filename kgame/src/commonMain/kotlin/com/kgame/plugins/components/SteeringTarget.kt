package com.kgame.plugins.components

import androidx.compose.ui.geometry.Offset
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.ecs.Entity

/**
 * A component of FollowTarget.
 * @param actor The entity to follow.
 */
data class FollowTarget(
    var actor: Entity,
    var minDistance: Float = 25f,
    var enabled: Boolean = true
) : Component<FollowTarget> {

    override fun type() = FollowTarget
    companion object Companion : ComponentType<FollowTarget>()

}

/**
 * Tracks another actor. The movement system will maintain [minDistance].
 */
fun FollowTarget.track(actor: Entity, minDistance: Float = this.minDistance) {
    this.actor = actor
    this.minDistance = minDistance
    this.enabled = true
}

/**
 * Discards the current tracking target. The actor loses interest in the target.
 */
fun FollowTarget.discard() {
    this.enabled = false
}

/**
 * A component of ArriveTarget.
 * @param position The target position to arrive at.
 */
data class ArriveTarget(
    var position: Offset,
    var stopDistance: Float = 5f,
    var enabled: Boolean = true
) : Component<ArriveTarget> {
    override fun type() = ArriveTarget
    companion object Companion : ComponentType<ArriveTarget>()
}

/**
 * Assigns a new destination. The actor will move towards this point
 * until it reaches the [stopDistance].
 */
fun ArriveTarget.assign(position: Offset, stopDistance: Float = this.stopDistance) {
    this.position = position
    this.stopDistance = stopDistance
    this.enabled = true
}

/**
 * Abandons the current destination. The actor will stop seeking this target.
 */
fun ArriveTarget.abandon() {
    this.enabled = false
}