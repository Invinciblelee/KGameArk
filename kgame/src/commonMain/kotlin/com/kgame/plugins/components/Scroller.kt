package com.kgame.plugins.components

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.ecs.Entity

data class Scroller(
    val speed: Float = 120f,
    val axis: Axis = Axis.Y
) : Component<Scroller> {
    override fun type() = Scroller
    companion object Companion : ComponentType<Scroller>()
}

enum class Axis { X, Y }

/**
 * Component that links a scroller to a target entity (usually the player).
 * This creates a relative movement effect to enhance the sense of speed.
 */
data class ScrollerTarget(
    val entity: Entity,
    val intensity: Float = 0.5f
) : Component<ScrollerTarget> {
    override fun type() = ScrollerTarget
    companion object Companion : ComponentType<ScrollerTarget>()
}