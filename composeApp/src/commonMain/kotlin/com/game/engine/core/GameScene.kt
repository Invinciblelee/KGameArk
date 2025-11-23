package com.game.engine.core

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.engine.asset.AssetKey
import com.game.engine.dsl.GameWorld
import kotlinx.atomicfu.atomic

@Immutable
class GameScene(
    val id: String,
    private val scope: GameScope,
    private val onEnter: (() -> Unit)?,
    private val onExit: (() -> Unit)?,
    private val update: ((Float) -> Unit)?,
    private val renderForeground: (DrawScope.() -> Unit)?,
    private val renderBackground: (DrawScope.() -> Unit)?,
    private val ui: (@Composable BoxScope.() -> Unit)?,
    private val world: GameWorld?,
    private val config: SceneConfig,
    private val assets: Set<AssetKey<*>> = emptySet()
) {

    private var isLoading by atomic(false)
    private var isActive by atomic(false)

    internal fun configure(params: Map<String, Any>) {
        config.update(params)
    }

    internal suspend fun load(progress: (Float) -> Unit) {
        if (isLoading) return

        if (assets.isEmpty()) {
            progress(1f)
            return
        }

        isLoading = true
        try {
            progress(0f)
            var index = 0
            for (key in assets) {
                scope.assets.load(key)
                progress(index.toFloat() / assets.size)
                index++
            }
            progress(1f)
        } finally {
            isLoading = false
        }
    }

    internal fun unload() {
        for (key in assets) {
            scope.assets.unload(key)
        }
    }

    fun enter() {
        isActive = true

        world?.enter()

        onEnter?.invoke()
    }

    fun exit() {
        isActive = false

        onExit?.invoke()

        world?.exit()
    }

    fun update(deltaTime: Float) {
        if (isActive) {
            // 1. ECS 优先更新
            world?.update(deltaTime)
        }

        // 2. 自定义脚本 DSL 更新
        update?.invoke(deltaTime)
    }

    fun render(drawScope: DrawScope) {
        // 1. 渲染背景
        renderBackground?.invoke(drawScope)
        if (isActive) {
            // 2. ECS渲染
            world?.render(drawScope)
        }
        // 3. 渲染前景
        renderForeground?.invoke(drawScope)
    }

    @Composable
    fun BoxScope.UI() {
        ui?.invoke(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GameScene

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}