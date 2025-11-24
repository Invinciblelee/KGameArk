package com.game.ecs.systems

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.ecs.Entity
import com.game.ecs.IntervalSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.collection.compareEntityBy
import com.game.ecs.components.Camera
import com.game.ecs.components.Renderable
import com.game.ecs.components.Transform
import com.game.engine.graphics.withCameraTransform
import com.game.engine.graphics.withLocalTransform
import kotlin.math.min

class RenderSystem : IntervalSystem() {
    private val cameraFamily = family { all(Camera, Transform) }
    private val renderableFamily = family { all(Renderable, Transform) }
    private val renderableComparator = compareEntityBy(Renderable)
    private val bounds = MutableRect(Offset.Zero, Size.Zero)
    private val cullRect = MutableRect(Offset.Zero, Size.Zero)

    override fun onRender(drawScope: DrawScope) {
        val cameraEntity = cameraFamily.find { it[Camera].isActive }

        if (cameraEntity != null) {
            drawScope.drawWithCamera(cameraEntity)
        } else {
            drawScope.drawDirectly()
        }
    }

    private fun DrawScope.drawWithCamera(camEntity: Entity) {
        val camera = camEntity[Camera]
        val camTrans = camEntity[Transform]

        withCameraTransform(camera, camTrans) {
            val worldW = size.width / camera.zoom
            val worldH = size.height / camera.zoom
            val finalX = camTrans.position.x + camera.shakeOffset.x
            val finalY = camTrans.position.y + camera.shakeOffset.y

            cullRect.set(
                finalX - worldW, finalY - worldH,
                finalX + worldW, finalY + worldH
            )

            cullRect.inflate(min(worldW, worldH) * 0.1f)

            drawRenderables(useCulling = true)
        }
    }

    private fun DrawScope.drawDirectly() {
        drawRenderables(useCulling = false)
    }

    private fun DrawScope.drawRenderables(useCulling: Boolean) {
        renderableFamily.sort(renderableComparator)
        renderableFamily.forEach {
            val renderable = it[Renderable]
            val transform = it[Transform]
            if (!renderable.isVisible) return@forEach

            val shouldDraw = if (useCulling && bounds.isFinite) {
                renderable.visual.getBounds(transform, bounds)
                cullRect.overlaps(bounds)
            } else {
                true
            }

            if (shouldDraw) {
                withLocalTransform(transform, size = renderable.visual.size) {
                    with(renderable.visual) { draw() }
                }
            }
        }
    }
}
