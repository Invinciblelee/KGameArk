package com.game.engine.ecs.systems

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.engine.ecs.Entity
import com.game.engine.ecs.System
import com.game.engine.ecs.components.Camera
import com.game.engine.ecs.components.Renderable
import com.game.engine.ecs.components.Transform
import com.game.engine.ecs.components.withCameraTransform
import com.game.engine.ecs.each
import com.game.engine.ecs.find
import com.game.engine.ecs.inject
import kotlin.math.min

// 假设这里是你的 World/System 文件，DrawScope.withCameraTransform 函数在这里是可见的

class RenderSystem : System() {
    private val cameraFamily by inject<Camera, Transform>()
    private val renderableFamily by inject<Renderable, Transform>()

    private val renderQueue = ArrayList<Entity>()

    override fun DrawScope.draw() {
        val cameraEntity = cameraFamily.find { it.get<Camera>().isActive }

        if (cameraEntity != null) {
            // --- 情况A：有相机 (使用封装好的函数) ---
            drawWithCamera(cameraEntity)
        } else {
            // --- 情况B：没相机 (简单直接绘制) ---
            drawDirectly()
        }
    }

    // 新增：利用你封装的函数，让代码变得极其简洁
    private fun DrawScope.drawWithCamera(camEntity: Entity) {
        // 确保类型匹配：将 ECS 组件视为你函数所需的参数类型
        val camera = camEntity.get<Camera>()  // 对应你函数中的 Camera
        val camTrans = camEntity.get<Transform>() // 对应你函数中的 Transform

        // ⚡️ 核心：直接调用你封装的扩展函数 ⚡️
        withCameraTransform(camera, camTrans) {
            // 1. 计算剔除范围 (Culling)
            val worldW = size.width / camera.zoom
            val worldH = size.height / camera.zoom
            val finalX = camTrans.position.x + camera.shakeOffset.x
            val finalY = camTrans.position.y + camera.shakeOffset.y

            // 简单扩大剔除范围 (防止边缘闪烁)
            val cullRect = Rect(
                finalX - worldW, finalY - worldH,
                finalX + worldW, finalY + worldH
            ).inflate(min(worldW, worldH) * 0.1f)

            // 2. 筛选、排序、绘制
            collectAndDraw(cullRect, useCulling = true)
        }
    }

    // 简单模式：直接遍历绘制
    private fun DrawScope.drawDirectly() {
        // 无剔除，直接绘制所有可见 Renderable
        collectAndDraw(null, useCulling = false)
    }

    // 逻辑分离：收集、排序、绘制的统一逻辑
    private fun DrawScope.collectAndDraw(cullRect: Rect?, useCulling: Boolean) {
        renderQueue.clear()

        // 收集阶段
        renderableFamily.each<Renderable, Transform> { entity, renderable, transform ->
            if (!renderable.isVisible) return@each

            val shouldDraw = if (useCulling) {
                // 有相机时，检查包围盒是否相交
                cullRect?.overlaps(renderable.visual.getBounds(transform)) ?: false
            } else {
                // 无相机时，全部绘制 (不进行剔除)
                true
            }

            if (shouldDraw) {
                renderQueue.add(entity)
            }
        }

        // 排序阶段
        renderQueue.sortBy { it.get<Renderable>().zIndex }

        // 绘制阶段
        for (i in renderQueue.indices) {
            val entity = renderQueue[i]
            val renderable = entity.get<Renderable>()
            val transform = entity.get<Transform>()

            with(renderable.visual) {
                draw(transform)
            }
        }
    }
}