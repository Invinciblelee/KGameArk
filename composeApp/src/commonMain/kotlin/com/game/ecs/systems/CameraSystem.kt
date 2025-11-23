package com.game.ecs.systems

import androidx.compose.ui.geometry.Offset
import com.game.ecs.ComponentType
import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.World.Companion.inject
import com.game.ecs.components.Camera
import com.game.ecs.components.CameraTarget
import com.game.ecs.components.SpringEffect
import com.game.ecs.components.Transform
import com.game.ecs.components.applyLerpFollow
import com.game.ecs.components.applySpringFollow
import com.game.ecs.injectables.ViewportTransform
import kotlin.random.Random

class CameraSystem(
    val viewportTransform: ViewportTransform = inject()
) : IteratingSystem(
    family = family { all(Camera, CameraTarget, Transform) }
) {

    override fun onTickEntity(entity: Entity) {
        val camera = entity[Camera]
        if (!camera.isActive) return

        val effect = entity.getOrNull(SpringEffect)
        val cameraTarget = entity[CameraTarget]
        val cameraTransform = entity[Transform]

        updateFollowLogic(deltaTime, camera, cameraTarget, cameraTransform, effect)

        updateShakeLogic(deltaTime, camera)
    }

    private fun updateFollowLogic(
        deltaTime: Float,
        camera: Camera,
        cameraTarget: CameraTarget,
        cameraTransform: Transform,
        effect: SpringEffect?
    ) {
        val targetTransform = cameraTarget.entity.getOrNull(Transform) ?: return
        val targetPosition = targetTransform.position

        if (effect != null) {
            cameraTransform.applySpringFollow(
                deltaTime = deltaTime,
                effect = effect,
                targetPosition = targetPosition
            )
        } else {
            cameraTransform.applyLerpFollow(
                deltaTime = deltaTime,
                targetPosition = targetPosition,
                lerpSpeed = camera.lerpSpeed
            )
        }

        var finalX = cameraTransform.position.x
        var finalY = cameraTransform.position.y
        val mapBounds = camera.mapBounds

        val viewportSize = viewportTransform.virtualSize

        if (mapBounds.isFinite) {
            val halfWidth = viewportSize.width / 2f
            val halfHeight = viewportSize.height / 2f

            // 限制 X 轴：左边界 + 半屏宽  ~  右边界 - 半屏宽
            val minX = mapBounds.left + halfWidth
            val maxX = mapBounds.right - halfWidth

            // 限制 Y 轴：上边界 + 半屏高  ~  下边界 - 半屏高
            val minY = mapBounds.top + halfHeight
            val maxY = mapBounds.bottom - halfHeight

            finalX = if (minX <= maxX) {
                finalX.coerceIn(minX, maxX)
            } else {
                mapBounds.center.x
            }

            finalY = if (minY <= maxY) {
                finalY.coerceIn(minY, maxY)
            } else {
                mapBounds.center.y
            }
        }

        cameraTransform.position = Offset(finalX, finalY)
    }

    private fun updateShakeLogic(dt: Float, camera: Camera) {
        if (camera.trauma > 0f) {
            camera.trauma = (camera.trauma - camera.traumaDecay * dt).coerceAtLeast(0f)
            val shakeFactor = camera.trauma * camera.trauma

            camera.shakeOffset = Offset(
                (Random.nextFloat() * 2 - 1) * camera.maxShakeOffset * shakeFactor,
                (Random.nextFloat() * 2 - 1) * camera.maxShakeOffset * shakeFactor
            )
            camera.shakeRotation =
                (Random.nextFloat() * 2 - 1) * camera.maxShakeAngle * shakeFactor
        } else {
            camera.shakeOffset = Offset.Zero
            camera.shakeRotation = 0f
        }
    }

}

inline fun <reified Target: ComponentType<Target>> CameraSystem.switchCamera(type: Target) {
    family.forEach {
        it[Camera].isActive = it[CameraTarget].entity has type
    }
}