package com.game.plugins.systems

import com.game.ecs.IntervalSystem
import com.game.ecs.World.Companion.inject
import com.game.plugins.services.CameraService

/**
 * System responsible for updating camera position, applying follow logic,
 * handling smooth transitions, screen shake, and boundary constraints.
 */
class CameraSystem(
    val cameraService: CameraService = inject()
) : IntervalSystem() {

    override fun onTick() {
        cameraService.director.update(deltaTime)
    }

}
