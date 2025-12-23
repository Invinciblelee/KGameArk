package com.kgame.engine.core

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kgame.ecs.World
import com.kgame.ecs.WorldCfgMarker
import com.kgame.ecs.WorldConfiguration
import com.kgame.ecs.configureWorld
import com.kgame.engine.dsl.GameDslMarker
import com.kgame.plugins.services.AnimationService
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.systems.AnimationSystem
import com.kgame.plugins.systems.AnimationTickSystem
import com.kgame.plugins.systems.CameraSystem
import com.kgame.plugins.systems.PhysicsSystem
import com.kgame.plugins.systems.RenderSystem
import com.kgame.plugins.systems.SteeringSystem
import com.kgame.plugins.systems.TiledMapCollisionSystem
import com.kgame.plugins.systems.TiledMapRenderSystem

internal class GameWorld(
    private val entityCapacity: Int,
    private val configuration: WorldConfiguration.() -> Unit,
    private val initWorld: World.() -> Unit
) {
    private var instance: World? = null

    private fun ensureWorld(): World {
        return instance ?: configureWorld(entityCapacity, configuration).also { instance = it }
    }

    fun init() {
        initWorld(ensureWorld())
    }

    fun update(deltaTime: Float) {
        instance?.update(deltaTime)
    }

    fun render(drawScope: DrawScope) {
        instance?.render(drawScope)
    }

    fun dispose() {
        instance?.dispose()
        instance = null
    }

}

@WorldCfgMarker
class GameWorldBuilder(
    private val scope: GameScope,
    private val entityCapacity: Int
) {

    private var useDefaultSystems = false

    private var configuration: WorldConfiguration.() -> Unit = {}
    private var initWorld: World.() -> Unit = {}

    fun useDefaultSystems() {
        useDefaultSystems = true
    }

    fun configure(configuration: WorldConfiguration.() -> Unit) {
        this.configuration = configuration
    }

    fun spawn(initWorld: World.() -> Unit) {
        this.initWorld = initWorld
    }

    private fun configureDefaults(world: World) {
        val scope = this@GameWorldBuilder.scope
        val useDefaultSystems = this@GameWorldBuilder.useDefaultSystems
        WorldConfiguration(world).apply {
            injectables {
                +scope
                +scope.input
                +scope.audio
                +scope.assets
                +scope.resolution
                +scope.textMeasurer
                +CameraService(scope.resolution)
                +AnimationService()
            }

            if (useDefaultSystems) {
                systems {
                    +SteeringSystem()
                    +PhysicsSystem()
                    +TiledMapCollisionSystem()
                    +AnimationTickSystem()
                    +AnimationSystem()
                    +CameraSystem()
                    +TiledMapRenderSystem()
                    +RenderSystem()
                }
            }
        }.configure()
    }

    internal fun build(): GameWorld {
        return GameWorld(
            entityCapacity = entityCapacity,
            configuration = {
                this@GameWorldBuilder.configureDefaults(world)
                this@GameWorldBuilder.configuration(this)
            },
            initWorld = initWorld
        )
    }

}