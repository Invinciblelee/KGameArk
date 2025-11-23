package com.game.ecs.components

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.ecs.Component
import com.game.ecs.ComponentType

// 2. 视觉接口 (Visual)
interface Visual {

    /**
     * 视觉尺寸, 当size==Size.Unspecified时，表示按实际尺寸渲染
     */
    val size: Size get() = Size.Unspecified

    /**
     * 绘制逻辑
     * 注意：RenderSystem 通常会预先应用 Matrix 变换（移到 pos，应用 rotation/scale），
     * 所以在 draw 内部，通常应该以 bounds.center 为中心绘制。
     */
    fun DrawScope.draw()

    /**
     * 获取世界坐标下的包围盒 (用于视锥剔除 Culling)
     * 需要用到 transform.pos 和 transform.scale
     */
    fun getBounds(transform: Transform, bounds: MutableRect) {
        if (size == Size.Unspecified) {
            bounds.set(
                Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY
            )
            return
        }

        // 考虑缩放后的半宽/半高
        val halfW = (size.width * transform.scaleX) / 2f
        val halfH = (size.height * transform.scaleY) / 2f

        // 构造以 transform.position 为中心的矩形
        bounds.left = transform.position.x - halfW
        bounds.top = transform.position.y - halfH
        bounds.right = transform.position.x + halfW
        bounds.bottom = transform.position.y + halfH
    }

}

// 3. 渲染组件 (Renderable)
data class Renderable(
    val visual: Visual,
    val zIndex: Int = 0,
    var isVisible: Boolean = true
) : Component<Renderable>, Comparable<Renderable> {
    override fun type() = Renderable

    companion object : ComponentType<Renderable>()

    override fun compareTo(other: Renderable): Int {
        return zIndex.compareTo(other.zIndex)
    }
}