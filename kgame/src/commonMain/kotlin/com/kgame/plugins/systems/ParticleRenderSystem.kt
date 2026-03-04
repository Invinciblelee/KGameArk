package com.kgame.plugins.systems

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.graphics.drawscope.withCameraTransform
import com.kgame.engine.graphics.drawscope.withCenteredTransform
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.Transform
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleService

class ParticleRenderSystem(
    private val particleService: ParticleService = inject(),
    private val cameraService: CameraService = inject(),
    priority: SystemPriority = SystemPriorityAnchors.Render
): IntervalSystem(priority = priority) {

    override fun onTick(deltaTime: Float) {
        particleService.update(deltaTime)
    }

    override fun onRender(drawScope: DrawScope) {
        val cameraEntity = cameraService.mainCameraEntity
        if (cameraEntity != null) {
            val camera = cameraEntity[Camera]
            val camTrans = cameraEntity[Transform]
            val camShake = cameraEntity.getOrNull(CameraShake)

            drawScope.withCameraTransform(camera, camTrans, camShake) {
               particleService.render(this)
            }
        } else {
            drawScope.withCenteredTransform {
                particleService.render(this)
            }
        }
    }

}