package com.kgame.plugins.systems

import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.World
import com.kgame.ecs.World.Companion.inject
import com.kgame.plugins.services.CameraService

/**
 * System responsible for updating camera position, applying follow logic,
 * handling smooth transitions, screen shake, and boundary constraints.
 */
class CameraSystem(
    private val cameraService: CameraService = inject()
) : IntervalSystem() {

    override fun onTick(deltaTime: Float) {
        cameraService.director.update(deltaTime)
    }

}
