package com.kgame.engine.core

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kgame.ecs.World
import com.kgame.ecs.WorldConfiguration
import com.kgame.ecs.configureWorld
import com.kgame.plugins.services.AnimationService
import com.kgame.plugins.services.CameraService

internal class GameWorld(
    private val scope: GameScope,
    private val entityCapacity: Int,
    private val configuration: WorldConfiguration.() -> Unit,
    private val initWorld: World.() -> Unit
) {
    private var instance: World? = null

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