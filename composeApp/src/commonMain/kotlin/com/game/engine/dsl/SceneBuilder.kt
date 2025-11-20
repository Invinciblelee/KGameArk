package com.game.engine.dsl

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.game.engine.core.GameScope
import com.game.engine.core.RuntimeScene
import com.game.engine.ecs.World

class SceneBuilder(private val scope: GameScope): GameScope by scope {
    private var onEnter: (() -> Unit)? = null
    private var onExit: (() -> Unit)? = null
    private var onUpdate: ((Float) -> Unit)? = null
    private var onRender: (DrawScope.() -> Unit)? = null

    private var activeWorld: World? = null

    @GameDsl
    fun world(block: World.() -> Unit): World {
        val world = World(scope)
        this.activeWorld = world.apply(block)
        return world
    }

    @GameDsl
    fun onEnter(block: () -> Unit) { onEnter = block }

    @GameDsl
    fun onExit(block: () -> Unit) { onExit = block }

    // 这里的 Receiver 是 GameScope，让用户可以直接调用 input.isKeyDown
    @GameDsl
    fun onUpdate(block: (Float) -> Unit) { onUpdate = block }

    @GameDsl
    fun onRender(block: DrawScope.() -> Unit) { onRender = block }

    fun build(): RuntimeScene = RuntimeScene(onEnter, onExit, onUpdate, onRender, activeWorld)
}