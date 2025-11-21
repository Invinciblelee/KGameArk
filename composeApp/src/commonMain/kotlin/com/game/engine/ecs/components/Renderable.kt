package com.game.engine.ecs.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.engine.ecs.Component

// 2. 视觉接口 (Visual)
interface Visual {

    val width: Float

    val height: Float

    /**
     * 绘制逻辑
     * 注意：RenderSystem 通常会预先应用 Matrix 变换（移到 pos，应用 rotation/scale），
     * 所以在 draw 内部，通常应该以 (0,0) 为中心绘制。
     * 传入 transform 是为了让 Visual 能获取额外信息（比如根据 scale 动态调整线宽）。
     */
    fun DrawScope.draw(transform: Transform)

    /**
     * 获取世界坐标下的包围盒 (用于视锥剔除 Culling)
     * 需要用到 transform.pos 和 transform.scale
     */
    fun getBounds(transform: Transform): Rect {
        // 考虑缩放后的半宽/半高
        val halfW = (width * transform.scaleX) / 2f
        val halfH = (height * transform.scaleY) / 2f

        // 构造以 transform.position 为中心的矩形
        return Rect(
            left = transform.position.x - halfW,
            top = transform.position.y - halfH,
            right = transform.position.x + halfW,
            bottom = transform.position.y + halfH
        )
    }
}

// 3. 渲染组件 (Renderable)
data class Renderable(
    val visual: Visual,
    val zIndex: Int = 0,
    var isVisible: Boolean = true
) : Component