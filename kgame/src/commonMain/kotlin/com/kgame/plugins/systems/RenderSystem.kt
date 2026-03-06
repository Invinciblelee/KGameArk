package com.kgame.plugins.systems

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.ecs.collection.compareEntityBy
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
    private val cameraService: CameraService = inject(),
    priority: SystemPriority = SystemPriorityAnchors.Render
): IteratingSystem(
    family = family { all(Transform, Renderable).none(Scroller) },
    comparator = compareEntityBy(Renderable),
    priority = priority
) {

    companion object {
        var isDebugging = false

        private val DebugStroke = Stroke(2f)
    }

    override fun onUpdate(deltaTime: Float) {
        super.onUpdate(deltaTime)

        family.forEach {
            it[Renderable].visual.update(deltaTime)
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

        val shouldDraw = cameraService.culler.overlaps(transform, renderable.bounds)

        if (shouldDraw) {
            drawScope.withLocalTransform(transform, renderable.size, renderable.anchor) {
                with(renderable.visual) { draw() }
            }

            if (isDebugging) {
                drawScope.drawDebugBounds(transform.position, renderable.size)
            }
        }
    }

    private fun DrawScope.drawDebugBounds(
        position: Offset,
        size: Size,
        color: Color = Color.Green
    ) {
        if (size.isUnspecified) return

        // Calculate the top-left corner of the AABB based on the entity's center position and size.
        // This is the only CPU calculation needed.
        val topLeftX = position.x - size.width / 2f
        val topLeftY = position.y - size.height / 2f

        // Draw a simple, non-rotated rectangle.
        // This is extremely fast and offloads the drawing to the GPU.
        drawRect(
            color = color,
            topLeft = Offset(topLeftX, topLeftY),
            size = size,
            style = DebugStroke
        )

        // Optional: Draw a small circle or cross at the entity's exact position (its center).
        // This helps to distinguish the position from the bounding box.
        drawCircle(
            color = Color.Yellow,
            radius = 4f,
            center = position
        )
    }


}
