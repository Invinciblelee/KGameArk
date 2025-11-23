package com.game.ecs.components

import com.game.ecs.Component
import com.game.ecs.ComponentType

// 标记该实体飞出屏幕会被销毁
data class Boundary(val padding: Float = 50f) : Component<Boundary> {
    override fun type() = Boundary
    companion object : ComponentType<Boundary>()
}