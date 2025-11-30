package com.game.engine.dsl

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.ecs.World
import com.game.ecs.WorldConfiguration
import com.game.engine.asset.AssetKey
import com.game.engine.core.GameEngine
import com.game.engine.core.GameScene
import com.game.engine.core.GameScope
import com.game.engine.core.GameWorld

@GameWorldMarker
class SceneBuilderScope<T: Any>(
    val key: T,
    private val engine: GameEngine
) : GameScope by engine {
    private var onProgress: ((Float) -> Unit)? = null
    private var onEnter: (() -> Unit)? = null
    private var onExit: (() -> Unit)? = null
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

    fun resources(builder: (AssetSet) -> Unit) {
        AssetSet(requiredAssets).also(builder).build()
    }

    fun onProgress(block: (Float) -> Unit) {
        onProgress = block
    }

    fun onEnter(block: () -> Unit) {
        onEnter = block
    }

    fun onExit(block: () -> Unit) {
        onExit = block
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
        onEnter,
        onExit,
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

    operator fun plusAssign(key: AssetKey<*, *>) {
        assets.add(key)
    }

    operator fun plusAssign(keys: Collection<AssetKey<*, *>>) {
        assets.addAll(keys)
    }

    internal fun build(): Set<AssetKey<*, *>> = assets

}
