package com.game.engine.core

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.engine.ecs.World

// 运行时场景容器
@Immutable
class RuntimeScene(
    val id: String,
    private val onEnter: (() -> Unit)?,
    private val onExit: (() -> Unit)?,
    private val update: ((Float) -> Unit)?,
    private val render: (DrawScope.() -> Unit)?,
    private val ui: (@Composable BoxScope.() -> Unit)?,
    private val world: World?,
    val scope: GameScope,
) {

    internal fun enter() {
        onEnter?.invoke()
    }

    internal fun exit() {
        onExit?.invoke()
    }

    internal fun update(deltaTime: Float) {
        // 1. ECS 优先更新
        world?.update(deltaTime)
        // 2. 自定义脚本 DSL 更新
        update?.invoke(deltaTime)
    }

    internal fun render(drawScope: DrawScope) {
        // 1. ECS 渲染
        world?.draw(drawScope)
        // 2. 自定义绘制 DSL
        render?.invoke(drawScope)
    }

    @Composable
    internal fun BoxScope.UI() {
        ui?.invoke(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RuntimeScene

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}