package com.kgame.engine.dsl

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kgame.ecs.World
import com.kgame.ecs.WorldConfiguration
import com.kgame.engine.asset.AssetKey
import com.kgame.engine.core.GameEngine
import com.kgame.engine.core.GameScene
import com.kgame.engine.core.GameScope
import com.kgame.engine.core.GameWorld

@GameDslMarker
class SceneBuilderScope<T: Any>(
    val key: T,
    private val engine: GameEngine
) : GameScope by engine {
    private var onProgress: ((Float) -> Unit)? = null
    private var onCreate: (suspend () -> Unit)? = null
    private var onStart: (() -> Unit)? = null
    private var onDispose: (() -> Unit)? = null
    private var onEnable: (() -> Unit)? = null
    private var onDisable: (() -> Unit)? = null
    private var onUpdate: ((Float) -> Unit)? = null
    private var onRender: (DrawScope.() -> Unit)? = null

    private var onForegroundUI: (@Composable BoxScope.() -> Unit)? = null
    private var onBackgroundUI: (@Composable BoxScope.() -> Unit)? = null

    private var world: GameWorld? = null

    private val requiredAssets = HashSet<AssetKey<*, *>>()

    fun world(
        capacity: Int = 1024,
        configuration: WorldConfiguration.() -> Unit,
        initWorld: World.() -> Unit
    ) {
        val world = GameWorld(
            engine,
            capacity,
            configuration,
            initWorld
        )
        this.world = world
    }

    fun resources(builder: AssetSet.() -> Unit) {
        AssetSet(requiredAssets).apply(builder).build()
    }

    fun onProgress(block: (Float) -> Unit) {
        onProgress = block
    }

    fun onCreate(block: suspend () -> Unit) {
        onCreate = block
    }

    fun onStart(block: () -> Unit) {
        onStart = block
    }

    fun onDispose(block: () -> Unit) {
        onDispose = block
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

    fun onForegroundUI(block: @Composable BoxScope.() -> Unit) {
        onForegroundUI = block
    }

    fun onBackgroundUI(block: @Composable BoxScope.() -> Unit) {
        onBackgroundUI = block
    }

    internal fun build(): GameScene<T> = GameScene(
        key,
        engine,
        onProgress,
        onStart,
        onDispose,
        onEnable,
        onDisable,
        onUpdate,
        onRender,
        onForegroundUI,
        onBackgroundUI,
        world,
        requiredAssets
    )
}

class AssetSet(
    private val assets: MutableSet<AssetKey<*, *>>
) {

    operator fun AssetKey<*, *>.unaryPlus() = assets.add(this)

    operator fun Collection<AssetKey<*, *>>.unaryPlus() = assets.addAll(this)

    internal fun build(): Set<AssetKey<*, *>> = assets

}
