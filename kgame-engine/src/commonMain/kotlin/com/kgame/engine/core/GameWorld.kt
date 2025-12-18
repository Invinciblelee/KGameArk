package com.kgame.engine.core

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.World
import com.kgame.ecs.WorldConfiguration
import com.kgame.ecs.configureWorld
import com.kgame.plugins.services.AnimationService
import com.kgame.plugins.services.CameraService

class GameWorld(
    private val scope: GameScope,
    private val entityCapacity: Int,
    private val configuration: WorldConfiguration.() -> Unit,
    private val initWorld: World.() -> Unit
) {
    @PublishedApi
    internal var instance: World? = null

    private fun ensureWorld(): World {
        return instance ?: configureWorld(entityCapacity) {
            internalInjectables {
                +scope
                +scope.input
                +scope.audio
                +scope.assets
                +scope.resolution
                +scope.textMeasurer
                +CameraService(scope.resolution)
                +AnimationService()
            }

            configuration()
        }.also { instance = it }
    }

    internal fun init() {
        initWorld(ensureWorld())
    }

    internal fun update(deltaTime: Float) {
        instance?.update(deltaTime)
    }

    internal fun render(drawScope: DrawScope) {
        instance?.render(drawScope)
    }

    internal fun dispose() {
        instance?.dispose()
        instance = null
    }

    inline fun <reified T> get(name: String = T::class.simpleName ?: T::class.toString()): T {
        val world = requireNotNull(instance) { "World has not been initialized yet." }
        return world.get(name)
    }

    inline fun <reified T : IntervalSystem> system(): T {
        val world = requireNotNull(instance) { "World has not been initialized yet." }
        return world.system()
    }

    inline fun <reified T : IntervalSystem> systemOrNull(): T? {
        return instance?.systemOrNull()
    }
}