package com.game.engine.ecs

import androidx.compose.ui.graphics.drawscope.DrawScope

abstract class System {
    private lateinit var _world: World
    val world: World get() = _world

    fun attach(world: World) {
        this._world = world
    }

    // 逻辑更新帧
    open fun update(dt: Float) {}

    // 渲染帧
    open fun draw(drawScope: DrawScope) {}
}