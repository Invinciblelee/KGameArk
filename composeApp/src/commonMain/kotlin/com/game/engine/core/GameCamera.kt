package com.game.engine.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.max
import kotlin.random.Random

class GameCamera {
    // 1. 基础属性
    var position: Offset = Offset.Zero // 世界坐标中心
    var zoom: Float = 1f
    
    // 2. 视口 (只读，用于剔除)
    var viewport: Rect = Rect.Zero
        private set

    // 3. 限制边界 (可选)
    private var _bounds: Rect = Rect.Zero
    val bounds: Rect get() = _bounds

    // 4. 震动参数 (Juice)
    private var shakeIntensity = 0f
    private var shakeDamping = 5f
    // 渲染时的偏移量 (position + shake)
    var renderPosition: Offset = Offset.Zero 
        private set

    // --- 核心更新逻辑 ---
    fun update(screenSize: Size, dt: Float) {
        // 1. 计算震动衰减
        var shakeOffset = Offset.Zero
        if (shakeIntensity > 0) {
            shakeOffset = Offset(
                (Random.nextFloat() - 0.5f) * shakeIntensity,
                (Random.nextFloat() - 0.5f) * shakeIntensity
            )
            shakeIntensity = max(0f, shakeIntensity - shakeDamping * dt)
        }

        // 2. 应用边界限制 (Clamping)
        if (!bounds.isEmpty) {
            // 计算视口一半的尺寸 (世界单位)
            val halfW = (screenSize.width / zoom) / 2f
            val halfH = (screenSize.height / zoom) / 2f
            
            val minX = bounds.left + halfW
            val maxX = bounds.right - halfW
            val minY = bounds.top + halfH
            val maxY = bounds.bottom - halfH

            // 如果地图比屏幕小，居中；否则钳制
            val x = if (minX > maxX) bounds.center.x else position.x.coerceIn(minX, maxX)
            val y = if (minY > maxY) bounds.center.y else position.y.coerceIn(minY, maxY)
            position = Offset(x, y)
        }

        // 3. 计算最终渲染位置 (逻辑位置 + 震动)
        renderPosition = position + shakeOffset

        // 4. 更新视口矩形 (用于 Culling)
        val viewW = screenSize.width / zoom
        val viewH = screenSize.height / zoom
        viewport = Rect(
            left = renderPosition.x - viewW / 2,
            top = renderPosition.y - viewH / 2,
            right = renderPosition.x + viewW / 2,
            bottom = renderPosition.y + viewH / 2
        )
    }

    // --- API ---

    fun setBounds(bounds: Rect) {
        _bounds = bounds
    }

    fun setBounds(left: Float, top: Float, right: Float, bottom: Float) {
        _bounds = Rect(left, top, right, bottom)
    }

    // 触发震动
    fun shake(amount: Float, damping: Float = 5f) {
        shakeIntensity += amount
        shakeDamping = damping
    }

    // 坐标转换: 屏幕 -> 世界 (用于鼠标点击)
    fun screenToWorld(screenPosition: Offset, screenSize: Size): Offset {
        val screenCenter = Offset(screenSize.width / 2, screenSize.height / 2)
        return (screenPosition - screenCenter) / zoom + renderPosition
    }

    // 坐标转换: 世界 -> 屏幕 (用于 UI 覆盖，如头顶血条)
    fun worldToScreen(worldPosition: Offset, screenSize: Size): Offset {
        val screenCenter = Offset(screenSize.width / 2, screenSize.height / 2)
        return (worldPosition - renderPosition) * zoom + screenCenter
    }
    
    // 瞬移
    fun teleport(position: Offset) {
        this@GameCamera.position = position
    }
    
    // 平滑移动 (Lerp) 辅助函数
    fun lerpTo(target: Offset, speed: Float, dt: Float) {
        val dx = (target.x - position.x) * speed * dt
        val dy = (target.y - position.y) * speed * dt
        position += Offset(dx, dy)
    }
}

fun DrawScope.withCamera(
    camera: GameCamera,
    block: DrawScope.(viewport: Rect) -> Unit
) {
    val screenSize = this.size
    val screenCenter = Offset(screenSize.width / 2, screenSize.height / 2)

    // 获取最终渲染位置 (包含了震动 shake 偏移)
    val camPos = camera.renderPosition
    val zoom = camera.zoom

    withTransform({
        // 变换顺序很重要：
        // 1. 将原点移动到屏幕中心
        translate(screenCenter.x, screenCenter.y)
        // 2. 应用缩放 (以原点为中心)
        scale(zoom, zoom, pivot = Offset.Zero)
        // 3. 反向移动相机位置 (模拟世界在动)
        translate(-camPos.x, -camPos.y)
    }) {
        // 回调给外部，并传入视口信息方便做 Culling
        block(camera.viewport)
    }
}