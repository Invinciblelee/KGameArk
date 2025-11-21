package com.game.engine.ecs.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.game.engine.ecs.Component

// 基础状态
data class Transform(
    var position: Offset = Offset.Zero,
    var rotation: Float = 0f,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f
) : Component

inline fun DrawScope.withTransform(
    transform: Transform,
    block: DrawScope.() -> Unit
) {
    withTransform({
        // 1. 移动到物体在世界中的绝对坐标
        // RenderSystem 已经处理了 Camera 变换，所以这里是世界坐标。
        translate(transform.position.x, transform.position.y)

        // 2. 应用物体自身的旋转
        rotate(transform.rotation)

        // 3. 应用物体自身的缩放
        // 注意：这里假设 transform.scaleX/Y 尚未被 getBounds 以外的逻辑使用
        scale(transform.scaleX, transform.scaleY)
    }) {
        block()
    }
}