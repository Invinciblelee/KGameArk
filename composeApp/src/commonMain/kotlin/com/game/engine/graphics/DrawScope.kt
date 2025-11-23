package com.game.engine.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.game.ecs.components.Camera
import com.game.ecs.components.Transform
import com.game.ecs.injectables.ViewportScaleType
import com.game.ecs.injectables.ViewportTransform

/**
 * DrawScope 扩展函数：使用 withTransform 应用屏幕适配的平移和缩放。
 * * 在 [block] 内部，DrawScope 的坐标系会被转换为游戏的虚拟坐标系。
 *
 * @param transform 包含缩放因子和偏移量的 ViewportTransform 实例。
 * @param block 在虚拟坐标系下执行的绘制逻辑。
 */
inline fun DrawScope.withViewportTransform(
    transform: ViewportTransform,
    block: DrawScope.() -> Unit
) {
    withTransform({
        if (transform.scaleType == ViewportScaleType.Fill) {
            translate(transform.offsetX, transform.offsetY)
        }
        scale(transform.scaleFactor, transform.scaleFactor, pivot = Offset.Zero)
    }) {
        block()
    }
}

/**
 * DrawScope 扩展函数：使用 withTransform 应用摄像机的平移、缩放和旋转。
 * 在 [block] 内部，DrawScope 的坐标系会被转换为摄像机的坐标系。
 *
 * @param camera 摄像机
 * @param transform 摄像机变化的 Transform
 * @param block 在摄像机坐标系下执行的绘制逻辑。
 */
inline fun DrawScope.withCameraTransform(
    camera: Camera,
    transform: Transform,
    block: DrawScope.() -> Unit
) {
    val viewportL = size.width * camera.viewport.left
    val viewportT = size.height * camera.viewport.top
    val viewportW = size.width * camera.viewport.width
    val viewportH = size.height * camera.viewport.height

    val centerX = viewportW / 2f
    val centerY = viewportH / 2f

    withTransform({
        // 1. 裁减
        clipRect(
            left = viewportL,
            top = viewportT,
            right = viewportL + viewportW,
            bottom = viewportT + viewportH
        )

        // 2. 移动到屏幕中心 (只需要移动到 Viewport 的中心即可)
        translate(centerX, centerY)

        // 3. 缩放
        scale(camera.zoom, camera.zoom, pivot = Offset.Zero)

        // 4. 旋转
        rotate(-(camera.rotation + transform.rotation + camera.shakeRotation))

        // 5. 位移
        val finalX = transform.position.x + camera.shakeOffset.x
        val finalY = transform.position.y + camera.shakeOffset.y
        translate(-finalX, -finalY)
    }) {
        block()
    }
}

/**
 * 设置实体在世界坐标系中的局部变换，并提供一个安全的、尺寸被覆盖的绘图上下文。
 * 这个函数将一个 DrawScope 转换为该实体自身的坐标系，允许内部的绘制代码
 * 仅以 (0, 0) 为原点，以其 size 为边界进行简洁绘图。
 * @param transform 包含实体在世界中的位置、旋转和缩放信息的 Transform 组件。
 * @param size 实体在虚拟空间中的标称尺寸 (例如 40f, 40f)。
 * @param block 实体本身的局部绘图逻辑 (例如 drawCircle(Color.Red))。
 */
inline fun DrawScope.withLocalTransform(
    transform: Transform,
    size: Size = Size.Unspecified,
    block: DrawScope.() -> Unit
) {
    val oldSize = drawContext.size
    try {
        drawContext.size = if (size.isUnspecified) oldSize else size
        withTransform({
            translate(transform.position.x, transform.position.y)
            rotate(transform.rotation)
            scale(transform.scaleX, transform.scaleY)
            if (size.isSpecified) {
                translate(-size.width / 2f, -size.height / 2f)
            }
        }) {
            block()
        }
    } finally {
        drawContext.size = oldSize
    }
}