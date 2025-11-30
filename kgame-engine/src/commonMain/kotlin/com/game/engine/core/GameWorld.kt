package com.game.engine.core

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.ecs.World
import com.game.ecs.WorldConfiguration
import com.game.ecs.configureWorld
import com.game.plugins.services.CameraService

internal class GameWorld(
    private val scope: GameScope,
    private val entityCapacity: Int,
    private val configuration: WorldConfiguration.() -> Unit,
    private val initWorld: World.() -> Unit
) {
    private var instance: World? = null

    private fun ensureWorld(): World {
        return instance ?: configureWorld(entityCapacity) {
            val cameraService = CameraService(scope.viewportTransform)

            internalInjectables {
                add(scope)
                add(scope.input)
                add(scope.audio)
                add(scope.assets)
                add(scope.viewportTransform)
                add(scope.textMeasurer)
                add(cameraService)
            }

            configuration()
        }.also { instance = it }
    }

    fun enter() {
        initWorld(ensureWorld())
    }

    fun update(deltaTime: Float) {
        instance?.update(deltaTime)
    }

    fun render(drawScope: DrawScope) {
        instance?.render(drawScope)
    }

    fun exit() {
        instance?.dispose()
    }

}