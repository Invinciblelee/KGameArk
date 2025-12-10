package com.game.plugins.systems

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.World.Companion.inject
import com.game.ecs.collection.compareEntityBy
import com.game.engine.graphics.drawscope.drawDebugBounds
import com.game.engine.graphics.drawscope.withCameraTransform
import com.game.engine.graphics.drawscope.withCenteredTransform
import com.game.engine.graphics.drawscope.withLocalTransform
import com.game.plugins.components.Camera
import com.game.plugins.components.Renderable
import com.game.plugins.components.Scroller
import com.game.plugins.components.Transform
import com.game.plugins.components.isHiding
import com.game.plugins.services.CameraService

class RenderSystem(
    val cameraService: CameraService = inject()
): IteratingSystem(
    family = family { all(Transform, Renderable); none(Scroller) },
    comparator = compareEntityBy(Renderable)
) {

    companion object {
        var isDebugging = true
    }

    override fun onRender(drawScope: DrawScope) {
        val cameraEntity = cameraService.activeCameraEntity
        if (cameraEntity != null) {
            val camera = cameraEntity[Camera]
            val camTrans = cameraEntity[Transform]

            drawScope.withCameraTransform(camera, camTrans) {
                super.onRender(this)
            }
        } else {
           drawScope.withCenteredTransform {
               super.onRender(this)
           }
        }
    }

    override fun onRenderEntity(entity: Entity, drawScope: DrawScope) {
        val renderable = entity[Renderable]
        val transform = entity[Transform]
        if (renderable.isHiding) return

        val shouldDraw = cameraService.culler.overlaps(transform)

        if (shouldDraw) {
            drawScope.withLocalTransform(transform) {
                with(renderable.visual) { draw() }
            }

            if (isDebugging) {
                drawScope.drawDebugBounds(transform)
            }
        }
    }

}
