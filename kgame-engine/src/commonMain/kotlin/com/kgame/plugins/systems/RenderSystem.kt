package com.kgame.plugins.systems

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.ecs.collection.compareEntityBy
import com.kgame.engine.graphics.drawscope.drawDebugBounds
import com.kgame.engine.graphics.drawscope.withCameraTransform
import com.kgame.engine.graphics.drawscope.withCenteredTransform
import com.kgame.engine.graphics.drawscope.withLocalTransform
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.Scroller
import com.kgame.plugins.components.Transform
import com.kgame.plugins.services.CameraService

class RenderSystem(
    private val cameraService: CameraService = inject()
): IteratingSystem(
    family = family { all(Transform, Renderable); none(Scroller) },
    comparator = compareEntityBy(Renderable)
) {

    companion object {
        var isDebugging = false
    }

    private val visibleWorldBounds = MutableRect(0f, 0f, 0f, 0f)
    private val visibleWorldStroke = Stroke(width = 10f)

    override fun onTick(deltaTime: Float) {
        if (isDebugging) {
            cameraService.culler.getBounds(visibleWorldBounds)
        }
    }

    override fun onRender(drawScope: DrawScope) {
        val cameraEntity = cameraService.mainCameraEntity
        if (cameraEntity != null) {
            val camera = cameraEntity[Camera]
            val camTrans = cameraEntity[Transform]
            val camShake = cameraEntity.getOrNull(CameraShake)

            drawScope.withCameraTransform(camera, camTrans, camShake) {
                super.onRender(this)

                if (isDebugging) {
                    drawRect(
                        color = Color.Green,
                        topLeft = visibleWorldBounds.topLeft,
                        size = visibleWorldBounds.size,
                        style = visibleWorldStroke
                    )
                }
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

        val shouldDraw = cameraService.culler.overlaps(transform, renderable.size)

        if (shouldDraw) {
            drawScope.withLocalTransform(transform, renderable.size) {
                with(renderable.visual) { draw() }
            }

            if (isDebugging) {
                drawScope.drawDebugBounds(transform, renderable.size)
            }
        }
    }

}
