package com.game.ecs.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.game.ecs.Component
import com.game.ecs.ComponentType
import com.game.ecs.Entity

// 标记：这个实体是被跟随的目标
data class CameraTarget(val entity: Entity) : Component<CameraTarget> {
    override fun type() = CameraTarget

    companion object : ComponentType<CameraTarget>()
}

// 数据：摄像机的参数
data class Camera(
    var isActive: Boolean = true,
    var isMain: Boolean = false,

    // 基础配置
    var zoom: Float = 1f,
    var rotation: Float = 0f, // 摄像机本身的基础旋转

    // 运动平滑配置
    var lerpSpeed: Float = 5f,
    var deadZone: Size = Size(50f, 50f),
    var mapBounds: Rect = Rect(
        Float.NEGATIVE_INFINITY,
        Float.NEGATIVE_INFINITY,
        Float.POSITIVE_INFINITY,
        Float.POSITIVE_INFINITY
    ),

    // 震动配置 (Trauma)
    var trauma: Float = 0f,        // 0~1
    var traumaDecay: Float = 2.0f, // 震动衰减速度
    var maxShakeOffset: Float = 50f,
    var maxShakeAngle: Float = 10f,

    // 渲染用临时状态 (System 计算后填入这里，供 Draw 使用)
    var shakeOffset: Offset = Offset.Zero,
    var shakeRotation: Float = 0f,
    var viewport: Rect = Rect(0f, 0f, 1f, 1f), // 默认全屏
) : Component<Camera> {

    override fun type() = Camera

    companion object : ComponentType<Camera>()

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