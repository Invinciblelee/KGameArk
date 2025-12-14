package com.kgame.plugins.components

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType

data class Scroller(
    val speed: Float = 120f,
    val axis: Axis = Axis.Y
) : Component<Scroller> {
    override fun type() = Scroller
    companion object Companion : ComponentType<Scroller>()
}

enum class Axis { X, Y }