package com.game.engine.geometry

import androidx.compose.ui.geometry.Offset
import com.game.ecs.Family
import com.game.engine.math.toRadians
import com.game.plugins.components.Camera
import com.game.plugins.components.Transform
import kotlin.math.cos
import kotlin.math.sin

interface CoordinateTransform {
    fun screenToWorld(position: Offset): Offset
    fun worldToScreen(position: Offset): Offset
}

class DefaultCoordinateTransform(
    val viewportTransform: ViewportTransform
): CoordinateTransform {
    private var cameraFamily: Family? = null

    internal fun setCameraFamily(family: Family) {
        cameraFamily = family
    }

    override fun screenToWorld(position: Offset): Offset {
        val family = cameraFamily ?: return position

        val camera: Camera
        val transform: Transform
        with(family) {
            val mainCameraEntity = find { it[Camera].isMain } ?: return position
            camera = mainCameraEntity[Camera]
            transform = mainCameraEntity[Transform]
        }

        val virtualSize = viewportTransform.virtualSize

        val cx = virtualSize.width / 2f
        val cy = virtualSize.height / 2f

        // A. 去中心化 (对应渲染时的 translate(viewportL + W/2...))
        // 鼠标原本是相对于左上角的，现在变成相对于中心的
        var x = position.x - cx
        var y = position.y - cy

        // B. 反缩放 (对应渲染时的 scale)
        if (camera.zoom != 0f) {
            x /= camera.zoom
            y /= camera.zoom
        }

        // C. 反旋转 (对应渲染时的 rotate)
        // 渲染旋转了 -rot，这里我们要转回 +rot
        val totalRotation = camera.rotation + camera.shakeRotation
        if (totalRotation != 0f) {
            val rad = toRadians(totalRotation.toDouble())
            val cos = cos(rad)
            val sin = sin(rad)

            val newX = x * cos - y * sin
            val newY = x * sin + y * cos
            x = newX.toFloat()
            y = newY.toFloat()
        }

        // D. 加上相机位移 (对应渲染时的 translate(-finalX, -finalY))
        // 现在加上相机的世界坐标，找回它在世界中的绝对位置
        val worldX = x + transform.position.x + camera.shakeOffset.x
        val worldY = y + transform.position.y + camera.shakeOffset.y

        return Offset(worldX, worldY)
    }

    override fun worldToScreen(position: Offset): Offset {
        val family = cameraFamily ?: return position

        val camera: Camera
        val transform: Transform
        with(family) {
            val mainCameraEntity = find { it[Camera].isMain } ?: return position
            camera = mainCameraEntity[Camera]
            transform = mainCameraEntity[Transform]
        }

        val virtualSize = viewportTransform.virtualSize

        val cx = virtualSize.width / 2f
        val cy = virtualSize.height / 2f

        // --- 正向推导 (和渲染顺序一致) ---

        // A. 减去相机位移
        var x = position.x - (transform.position.x + camera.shakeOffset.x)
        var y = position.y - (transform.position.y + camera.shakeOffset.y)

        // B. 旋转
        val totalRotation = camera.rotation + camera.shakeRotation
        if (totalRotation != 0f) {
            // 渲染时是负旋转
            val rad = toRadians(-totalRotation.toDouble())
            val cos = cos(rad)
            val sin = sin(rad)
            val newX = x * cos - y * sin
            val newY = x * sin + y * cos
            x = newX.toFloat()
            y = newY.toFloat()
        }

        // C. 缩放
        x *= camera.zoom
        y *= camera.zoom

        // D. 加上中心偏移
        return Offset(x + cx, y + cy)
    }
}