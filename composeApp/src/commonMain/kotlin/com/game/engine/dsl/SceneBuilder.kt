package com.game.engine.dsl

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.ecs.World
import com.game.ecs.WorldConfiguration
import com.game.ecs.components.Camera
import com.game.ecs.configureWorld
import com.game.engine.asset.AssetKey
import com.game.engine.core.GameScene
import com.game.engine.core.GameScope
import com.game.engine.core.SceneConfig
import com.game.engine.geometry.DefaultCoordinateTransform

class SceneBuilder(
    private val id: String,
    private val scope: GameScope
) : GameScope by scope {
    private var onEnter: (() -> Unit)? = null
    private var onExit: (() -> Unit)? = null
    private var onUpdate: ((Float) -> Unit)? = null
    private var onRenderForeground: (DrawScope.() -> Unit)? = null
    private var onRenderBackground: (DrawScope.() -> Unit)? = null

    private var onUI: (@Composable BoxScope.() -> Unit)? = null

    private var world: GameWorld? = null

    val config = SceneConfig()

    private val requiredAssets = HashSet<AssetKey<*>>()

    @GameDslMarker
    fun world(
        capacity: Int = 1024,
        configuration: WorldConfiguration.() -> Unit
    ): World {
        val world = GameWorld(
            scope,
            capacity,
            configuration
        )
        this.world = world
        return world.instance
    }

    @GameDslMarker
    fun resources(builder: (AssetSet) -> Unit) {
        AssetSet(requiredAssets).also(builder).build()
    }

    @GameDslMarker
    fun onEnter(block: () -> Unit) {
        onEnter = block
    }

    @GameDslMarker
    fun onExit(block: () -> Unit) {
        onExit = block
    }

    @GameDslMarker
    fun onUpdate(block: (Float) -> Unit) {
        onUpdate = block
    }

    @GameDslMarker
    fun onRenderForeground(block: DrawScope.() -> Unit) {
        onRenderForeground = block
    }

    @GameDslMarker
    fun onRenderBackground(block: DrawScope.() -> Unit) {
        onRenderBackground = block
    }

    @GameDslMarker
    fun onUI(block: @Composable BoxScope.() -> Unit) {
        onUI = block
    }

    internal fun build(): GameScene = GameScene(
        id,
        scope,
        onEnter,
        onExit,
        onUpdate,
        onRenderForeground,
        onRenderBackground,
        onUI,
        world,
        config,
        requiredAssets
    )
}

@GameDslMarker
class AssetSet(
    private val assets: MutableSet<AssetKey<*>>
) {

    operator fun plusAssign(key: AssetKey<*>) {
        assets.add(key)
    }

    operator fun plusAssign(keys: Collection<AssetKey<*>>) {
        assets.addAll(keys)
    }

    internal fun build(): Set<AssetKey<*>> = assets

}

class GameWorld(
    private val scope: GameScope,
    entityCapacity: Int,
    configuration: WorldConfiguration.() -> Unit,
) {

    internal val instance = configureWorld(entityCapacity) {
        injectables {
            add(scope)
            add(scope.input)
            add(scope.audio)
            add(scope.assets)
            add(scope.viewportTransform)
            add(scope.coordinateTransform)
            add(scope.textMeasurer)
        }
        configuration()
    }

    fun enter() {
        val cameraFamily = instance.family { all(Camera) }
        val coordinateTransform = scope.coordinateTransform
        if (coordinateTransform is DefaultCoordinateTransform) {
            coordinateTransform.setCameraFamily(cameraFamily)
        }
    }

    fun update(deltaTime: Float) {
        instance.update(deltaTime)
    }

    fun render(drawScope: DrawScope) {
        instance.render(drawScope)
    }

    fun exit() {
        instance.dispose()
    }

}