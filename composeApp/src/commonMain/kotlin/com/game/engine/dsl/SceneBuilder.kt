package com.game.engine.dsl

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.game.engine.core.GameScope
import com.game.engine.core.RuntimeScene
import com.game.engine.ecs.World

class SceneBuilder(
    private val id: String,
    private val scope: GameScope
): GameScope by scope {
    private var onEnter: (() -> Unit)? = null
    private var onExit: (() -> Unit)? = null
    private var onUpdate: ((Float) -> Unit)? = null
    private var onRender: (DrawScope.() -> Unit)? = null

    private var onUI: (@Composable BoxScope.() -> Unit)? = null

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

    @GameDsl
    fun onUpdate(block: (Float) -> Unit) { onUpdate = block }

    @GameDsl
    fun onRender(block: DrawScope.() -> Unit) { onRender = block }

    @GameDsl
    fun onUI(block: @Composable BoxScope.() -> Unit) { onUI = block }

    fun build(): RuntimeScene = RuntimeScene(id, onEnter, onExit, onUpdate, onRender, onUI, activeWorld, scope)
}