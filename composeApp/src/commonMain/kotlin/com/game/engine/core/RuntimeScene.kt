package com.game.engine.core

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.engine.ecs.World

// 运行时场景容器
class RuntimeScene(
    val onEnter: (() -> Unit)?,
    val onExit: (() -> Unit)?,
    val updateLogic: ((Float) -> Unit)?,
    val renderLogic: (DrawScope.() -> Unit)?,
    private val world: World?
) {
    private lateinit var _engine: GameEngine
    val engine: GameEngine get() = _engine

    fun bind(engine: GameEngine) {
        this._engine = engine
    }

    fun update(dt: Float) {
        // 1. ECS 优先更新
        world?.update(dt)
        // 2. 自定义脚本 DSL 更新
        updateLogic?.invoke(dt)
    }

    fun render(drawScope: DrawScope) {
        // 1. ECS 渲染
        world?.draw(drawScope)
        // 2. 自定义绘制 DSL
        renderLogic?.invoke(drawScope)
    }
}