package com.game.engine.ecs.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.game.engine.ecs.Component

// 标记：这个实体是被跟随的目标
data class CameraTarget(val id: Int) : Component

// 数据：摄像机的参数
data class Camera(
    var isActive: Boolean = true,

    // 基础配置
    var zoom: Float = 1f,
    var rotation: Float = 0f, // 摄像机本身的基础旋转

    // 运动平滑配置
    var lerpSpeed: Float = 5f,
    var deadZone: Size = Size(50f, 50f),
    var mapBounds: Rect? = null,

    // 震动配置 (Trauma)
    var trauma: Float = 0f,        // 0~1
    var traumaDecay: Float = 2.0f, // 震动衰减速度
    var maxShakeOffset: Float = 50f,
    var maxShakeAngle: Float = 10f,

    // 渲染用临时状态 (System 计算后填入这里，供 Draw 使用)
    var shakeOffset: Offset = Offset.Zero,
    var shakeRotation: Float = 0f,
    var viewport: Rect = Rect(0f, 0f, 1f, 1f) // 默认全屏
) : Component

// 现在转换逻辑是 Component 的扩展方法，纯数学计算
fun Camera.screenToWorld(screenPos: Offset, cameraPos: Offset, screenSize: Size): Offset {
    val center = Offset(screenSize.width / 2, screenSize.height / 2)
    // 公式：(屏幕 - 中心) / 缩放 + (相机物理位置 + 震动偏移)
    return (screenPos - center) / zoom + (cameraPos + shakeOffset)
}

fun Camera.worldToScreen(worldPos: Offset, cameraPos: Offset, screenSize: Size): Offset {
    val center = Offset(screenSize.width / 2, screenSize.height / 2)
    return (worldPos - (cameraPos + shakeOffset)) * zoom + center
}

/**
 * 施加震动
 */
fun Camera.addTrauma(amount: Float) {
    trauma = (trauma + amount).coerceIn(0f, 1f)
}


/**
 * 【新增】基于中心点和尺寸设置地图边界。
 * @param mapWidth 地图总宽度
 * @param mapHeight 地图总高度
 * @param centerX 地图原点X坐标 (默认为 0)
 * @param centerY 地图原点Y坐标 (默认为 0)
 */
fun Camera.setMapBounds(
    mapWidth: Float,
    mapHeight: Float,
    centerX: Float = 0f,
    centerY: Float = 0f
) {
    mapBounds = Rect(
        left = centerX - mapWidth / 2f,
        top = centerY - mapHeight / 2f,
        right = centerX + mapWidth / 2f,
        bottom = centerY + mapHeight / 2f
    )
}

/**
 * 【新增】设置自定义视口，参数为 0.0 到 1.0 的相对屏幕比例。
 * @param left 相对左边界
 * @param top 相对上边界
 * @param width 相对宽度
 * @param height 相对高度
 */
fun Camera.setViewportNormalized(
    left: Float,
    top: Float,
    width: Float,
    height: Float
) {
    viewport = Rect(left, top, left + width, top + height)
}

/**
 * 内部辅助：应用摄像机变换
 */
inline fun DrawScope.withCameraTransform(
    camera: Camera,
    transform: Transform,
    block: DrawScope.() -> Unit
) {
    val viewportW = size.width * camera.viewport.width
    val viewportH = size.height * camera.viewport.height
    val viewportL = size.width * camera.viewport.left
    val viewportT = size.height * camera.viewport.top

    clipRect(viewportL, viewportT, viewportL + viewportW, viewportT + viewportH) {
        withTransform({
            // 1. 移动到屏幕中心
            translate(viewportL + viewportW / 2f, viewportT + viewportH / 2f)

            // 2. 缩放
            scale(camera.zoom, camera.zoom, pivot = Offset.Zero)

            // 3. 旋转 (摄像机旋转 + 震动旋转)
            // 摄像机旋转意味着世界反向旋转，所以取负
            rotate(-(camera.rotation + transform.rotation + camera.shakeRotation))

            // 4. 位移 (摄像机物理位置 + 震动偏移)
            // 同样，摄像机向右移 = 世界向左移，取负
            // 注意：shakeOffset 是视觉层面的抖动，直接叠加在这里
            val finalX = transform.position.x + camera.shakeOffset.x
            val finalY = transform.position.y + camera.shakeOffset.y
            translate(-finalX, -finalY)
        }) {
            block()
        }
    }
}