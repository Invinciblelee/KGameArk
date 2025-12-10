package com.game.plugins.systems

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ScaleFactor
import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.engine.graphics.drawscope.withLocalTransform
import com.game.plugins.components.Axis
import com.game.plugins.components.Renderable
import com.game.plugins.components.Scroller
import com.game.plugins.components.Transform

/**
 * ScrollerRenderSystem renders seamlessly repeating background tiles
 * that carry a ScrollLock component. It draws only two tiles per frame
 * (head & tail) along the locked axis (X or Y), ensuring zero-allocation
 * infinite scroll with built-in viewport culling.
 */
class ScrollerRenderSystem :
    IteratingSystem(family { all(Renderable, Transform, Scroller) }) {

    private val renderTransform = Transform()

    override fun onRenderEntity(entity: Entity, drawScope: DrawScope) {
        val renderable = entity[Renderable]
        val trans = entity[Transform]
        val scroller = entity[Scroller]
        val axis = scroller.axis

        // 计算 scale，确保背景在滚动方向上铺满屏幕
        val scale = when (axis) {
            Axis.X -> drawScope.size.height / trans.size.height
            Axis.Y -> drawScope.size.width / trans.size.width
        }

        // 计算每一块 tile 的实际尺寸
        val tileSize = when (axis) {
            Axis.X -> trans.size.width * scale
            Axis.Y -> trans.size.height * scale
        }

        // 计算屏幕范围
        val canvasWidth = drawScope.size.width
        val canvasHeight = drawScope.size.height

        // 计算当前 tile 的起始位置
        var drawPos = when (axis) {
            Axis.X -> trans.position.x
            Axis.Y -> trans.position.y
        }

        // 检查滚动方向
        val isUpward = scroller.speed > 0

        // 计算需要绘制的背景图片数量
        val numTiles = when (axis) {
            Axis.X -> (canvasWidth / tileSize).toInt() + 1
            Axis.Y -> (canvasHeight / tileSize).toInt() + 1
        }

        // 动态绘制背景图片，直到覆盖整个屏幕范围
        var tileIndex = 0
        while (tileIndex < numTiles) {
            // 计算当前 tile 的位置
            val position = when (axis) {
                Axis.X -> Offset(drawPos, canvasHeight / 2f)
                Axis.Y -> Offset(canvasWidth / 2f, drawPos)
            }

            // 设置 transform 参数
            renderTransform.position = position
            renderTransform.size = trans.size
            renderTransform.scale = ScaleFactor(scale, scale)

            // 绘制当前 tile
            drawScope.withLocalTransform(renderTransform) {
                with(renderable.visual) { draw() }
            }

            // 更新 drawPos 以绘制下一张背景图片
            drawPos += when (axis) {
                Axis.X -> tileSize
                Axis.Y -> tileSize
            }

            // 更新 tileIndex
            tileIndex++
        }
    }
}