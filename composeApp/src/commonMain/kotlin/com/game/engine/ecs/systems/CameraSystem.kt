package com.game.engine.ecs.systems

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import com.game.engine.ecs.System
import com.game.engine.ecs.components.Camera
import com.game.engine.ecs.components.CameraTarget
import com.game.engine.ecs.components.Transform
import com.game.engine.ecs.each
import com.game.engine.ecs.inject
import kotlin.random.Random

class CameraSystem : System() {
    private val cameraFamily by inject<Camera, CameraTarget, Transform>()

    override fun update(deltaTime: Float) {
        cameraFamily.each<Camera, CameraTarget, Transform> { _, camera, cameraTarget, cameraTransform ->
            if (!camera.isActive) return@each

            // 1. 跟随逻辑 (修改 camTransform.position)
            updateFollowLogic(deltaTime, camera, cameraTarget, cameraTransform)

            // 2. 震动逻辑 (修改 camera.shakeOffset / shakeRotation)
            updateShakeLogic(deltaTime, camera)
        }
    }

    private fun updateFollowLogic(
        deltaTime: Float,
        camera: Camera,
        cameraTarget: CameraTarget,
        cameraTransform: Transform
    ) {
        val targetEntity = world.find(cameraTarget.id) ?: return
        val targetTransform = targetEntity.getOrNull<Transform>() ?: return

        // Lerp 插值计算新位置
        val t = (camera.lerpSpeed * deltaTime).coerceIn(0f, 1f)
        val newX = lerp(cameraTransform.position.x, targetTransform.position.x, t)
        val newY = lerp(cameraTransform.position.y, targetTransform.position.y, t)

        // 边界限制 (如果有)
        var finalX = newX
        var finalY = newY
        camera.mapBounds?.let { bounds ->
            finalX = finalX.coerceIn(bounds.left, bounds.right)
            finalY = finalY.coerceIn(bounds.top, bounds.bottom)
        }

        cameraTransform.position = Offset(finalX, finalY)
    }

    private fun updateShakeLogic(dt: Float, camera: Camera) {
        if (camera.trauma > 0f) {
            camera.trauma = (camera.trauma - camera.traumaDecay * dt).coerceAtLeast(0f)
            val shakeFactor = camera.trauma * camera.trauma

            // 计算震动数值写入 Component
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