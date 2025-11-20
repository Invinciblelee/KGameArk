package com.game.engine.ecs.systems

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.game.engine.ecs.System
import com.game.engine.ecs.components.Camera
import com.game.engine.ecs.components.Transform
import com.game.engine.ecs.family

class CameraSystem(
    private val mapBounds: Rect? = null
) : System() {
    private val cameras by family<Camera, Transform>()

    override fun update(dt: Float) {
        cameras.forEach { entity, cam, t ->
            if (cam.isActive) {
                // 1. 限制边界 (Clamping)
                // 防止摄像机看到地图外面的黑边
                // 这一步必须在 Steering/Physics 更新完位置之后做
                val viewportHalfW = (cam.viewportSize.width / cam.zoom) / 2f
                val viewportHalfH = (cam.viewportSize.height / cam.zoom) / 2f
                
                t.position = Offset(
                    x = t.position.x.coerceIn(mapBounds.left + viewportHalfW, mapBounds.right - viewportHalfW),
                    y = t.position.y.coerceIn(mapBounds.top + viewportHalfH, mapBounds.bottom - viewportHalfH)
                )

                // 2. 处理震动衰减 (Screen Shake Decay)
                if (cam.shakeIntensity > 0) {
                    cam.shakeIntensity -= 5f * dt
                    if (cam.shakeIntensity < 0) cam.shakeIntensity = 0f
                    
                    // 施加震动偏移
                    t.position += Offset.random() * cam.shakeIntensity
                }
            }
        }
    }
}