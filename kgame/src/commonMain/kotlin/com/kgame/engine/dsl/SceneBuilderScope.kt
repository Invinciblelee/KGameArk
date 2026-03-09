package com.kgame.engine.dsl

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kgame.engine.core.GameEngine
import com.kgame.engine.core.GameScene
import com.kgame.engine.core.GameScope
import com.kgame.engine.core.GameWorld
import com.kgame.engine.core.GameWorldBuilder

@GameDslMarker
class SceneBuilderScope<T: Any>(
    val key: T,
    private val engine: GameEngine
) : GameScope by engine {
    private var onCreate: (suspend () -> Unit)? = null
    private var onStart: (() -> Unit)? = null
    private var onDestroy: (() -> Unit)? = null
    private var onEnable: (() -> Unit)? = null
    private var onDisable: (() -> Unit)? = null
    private var onUpdate: ((Float) -> Unit)? = null
    private var onRender: (DrawScope.() -> Unit)? = null

    private var onForegroundUI: (@Composable () -> Unit)? = null
    private var onBackgroundUI: (@Composable () -> Unit)? = null

    private var world: GameWorld? = null

    fun onWorld(
        capacity: Int = 1024,
        builder: GameWorldBuilder.() -> Unit
    ) {
        world = GameWorldBuilder(engine, capacity).apply(builder).build()
    }

    fun onCreate(block: suspend () -> Unit) {
        onCreate = block
    }

    fun onStart(block: () -> Unit) {
        onStart = block
    }

    fun onDestroy(block: () -> Unit) {
        onDestroy = block
    }

    fun onEnable(block: () -> Unit) {
        onEnable = block
    }

    fun onDisable(block: () -> Unit) {
        onDisable = block
    }

    fun onUpdate(block: (Float) -> Unit) {
        onUpdate = block
    }

    fun onRender(block: DrawScope.() -> Unit) {
        onRender = block
    }

    fun onForegroundUI(block: @Composable () -> Unit) {
        onForegroundUI = block
    }

    fun onBackgroundUI(block: @Composable () -> Unit) {
        onBackgroundUI = block
    }

    internal fun build(): GameScene<T> = GameScene(
        key,
        engine,
        onCreate,
        onStart,
        onDestroy,
        onEnable,
        onDisable,
        onUpdate,
        onRender,
        onForegroundUI,
        onBackgroundUI,
        world
    )
}
