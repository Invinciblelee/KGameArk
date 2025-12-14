package com.example.kgame.animations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

// 3D 点结构
data class Vector3(var x: Float, var y: Float, var z: Float)

@Composable
fun HyperspaceTunnelAnimation(modifier: Modifier = Modifier) {
    // --- 核心配置 ---
    val ringsCount = 30         // 隧道环的数量 (越多越深邃，但也越吃性能)
    val ringRadius = 400f       // 隧道半径
    val segmentCount = 6        // 形状：6=六边形, 0=圆形(模拟), 4=正方形
    val maxDepth = 2000f        // 隧道总深度
    val fovBase = 800f          // 基础视野距离

    // --- 状态 ---
    // 预生成隧道数据：每一环都是一组 3D 点
    val tunnel = remember {
        List(ringsCount) { r ->
            // 深度均匀分布
            val z = maxDepth * (r.toFloat() / ringsCount)
            // 生成一圈点
            val points = List(segmentCount) { i ->
                val angle = (i.toFloat() / segmentCount) * 2 * PI
                Vector3(
                    x = cos(angle).toFloat() * ringRadius,
                    y = sin(angle).toFloat() * ringRadius,
                    z = z
                )
            }
            // 存储这一环的数据：z 深度和点列表
            mutableStateOf(Pair(z, points))
        }
    }

    var speed by remember { mutableStateOf(20f) }
    var rotation by remember { mutableStateOf(0f) }
    
    // 交互状态
    var cameraOffset by remember { mutableStateOf(Offset.Zero) } // 摄像机偏移(弯道)
    var isWarping by remember { mutableStateOf(false) } // 是否加速中
    var warpFactor by remember { mutableStateOf(0f) }   // 加速视觉因子 (0~1)
    
    var frameTrigger by remember { mutableStateOf(0L) }

    // 复用 Path 对象，避免 GC 爆炸
    val pathBuffer = remember { Path() }

    // --- 动画循环 ---
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nanos ->
                frameTrigger = nanos
                
                // 1. 速度与状态逻辑
                val targetSpeed = if (isWarping) 150f else 10f
                speed += (targetSpeed - speed) * 0.05f // 平滑变速
                
                val targetWarp = if (isWarping) 1f else 0f
                warpFactor += (targetWarp - warpFactor) * 0.1f
                
                // 旋转隧道
                rotation += 0.005f + (warpFactor * 0.02f)

                // 2. 移动隧道
                tunnel.forEach { ringState ->
                    val (currentZ, points) = ringState.value
                    var newZ = currentZ - speed
                    
                    // 循环：如果跑到相机后面了，扔到最远处
                    if (newZ <= 1f) {
                        newZ += maxDepth
                    }
                    
                    ringState.value = currentZ to points // 只要改Z，点是相对坐标不用动
                    // 这里的写法有点 trick，实际上我们更新的是 newZ，下面渲染时会用到
                    // 为了性能，我们不直接修改不可变的 Pair，而是用变量存 Z
                    // 这里为了简单演示，重新赋值 Pair，实际上最好是 mutable class
                }
                
                // 修正: 上面的 Pair 更新逻辑在 Compose State 中可能效率低，
                // 我们直接修改 mutable class 会更好。但为了代码简洁，我们在 draw 阶段计算 newZ
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { cameraOffset = Offset.Zero },
                    onDragEnd = { 
                        // 松手后慢慢回正的逻辑放在 draw loop 里做平滑插值会更好，
                        // 这里简单处理，松手不立即回零，而是让它保留一点惯性
                    },
                    onDrag = { change, dragAmount ->
                        // 拖动改变“弯道”曲率
                        cameraOffset += dragAmount * 1.5f
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isWarping = true
                        tryAwaitRelease()
                        isWarping = false
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tick = frameTrigger // 驱动刷新
            val centerX = size.width / 2
            val centerY = size.height / 2
            
            // 平滑回正相机 (自动驾驶修正)
            if (!isWarping) {
                cameraOffset *= 0.95f
            }

            // 动态 FOV：加速时视野拉远，产生速度感
            val currentFov = fovBase + warpFactor * 300f 

            // 预计算旋转矩阵
            val cosR = cos(rotation)
            val sinR = sin(rotation)

            // --- 渲染阶段 ---
            // 我们需要保存上一环的投影点，用来画连接线 (纵向线框)
            var prevProjectedPoints: List<Offset>? = null
            
            // 为了排序，我们需要手动管理 Z 轴变化。
            // 这里我们做一个技巧：每一帧重新计算 Z 位置，不依赖 State 存储具体值
            // 使用 tick 计算偏移量
            val totalOffset = (tick / 1_000_000f) * (if(isWarping) 0.15f else 0.01f) * 100f
            
            // 遍历每一环
            for (i in 0 until ringsCount) {
                // 计算当前环的 Z 深度 (加上时间偏移实现移动)
                val rawZ = (maxDepth * (i.toFloat() / ringsCount) - totalOffset) % maxDepth
                // 修正负数模运算，保证 Z 在 0~maxDepth 之间
                val z = if (rawZ < 0) rawZ + maxDepth else rawZ
                
                // 如果太近（穿过相机），就不画
                if (z < 10f) continue

                // 弯道计算：根据 Z 深度，计算 X/Y 的非线性偏移
                // 离得越远，偏移越大 -> 形成弯曲效果
                val curveRatio = (z / maxDepth)
                val curveRatioSq = curveRatio * curveRatio
                val offsetX = cameraOffset.x * curveRatioSq
                val offsetY = cameraOffset.y * curveRatioSq

                // 颜色计算：深度渐变 + Warp 白热化
                // 远处理论上暗，近处亮。加速时整体变亮。
                val depthAlpha = (1f - z / maxDepth).coerceIn(0f, 1f)
                val baseColor = hsvToColor(
                    hue = (z * 0.1f + tick / 10000000f) % 360f, // 颜色随深度和时间流动
                    saturation = 1f - warpFactor * 0.8f, // 加速时变白 (饱和度降低)
                    value = 1f
                )
                val finalColor = baseColor.copy(alpha = depthAlpha)

                // 投影当前环的所有点
                val projectedPoints = ArrayList<Offset>()
                val points3D = tunnel[i].value.second // 取出这一环的原始点形状

                points3D.forEach { p3d ->
                    // 1. 旋转 (绕 Z 轴)
                    val rx = p3d.x * cosR - p3d.y * sinR
                    val ry = p3d.x * sinR + p3d.y * cosR
                    
                    // 2. 弯道偏移 (加在世界坐标上)
                    val wx = rx - offsetX
                    val wy = ry - offsetY

                    // 3. 透视投影
                    val scale = currentFov / (currentFov + z)
                    val sx = centerX + wx * scale
                    val sy = centerY + wy * scale
                    
                    projectedPoints.add(Offset(sx.toFloat(), sy.toFloat()))
                }

                // --- 绘制 ---
                pathBuffer.reset()
                if (projectedPoints.isNotEmpty()) {
                    pathBuffer.moveTo(projectedPoints[0].x, projectedPoints[0].y)
                    for (j in 1 until projectedPoints.size) {
                        pathBuffer.lineTo(projectedPoints[j].x, projectedPoints[j].y)
                    }
                    pathBuffer.close() // 闭合六边形
                }

                // 1. 画横向环 (Ring)
                // 加速时线条变粗
                val strokeWidth = (3f + warpFactor * 5f) * (currentFov / (currentFov + z))
                drawPath(
                    path = pathBuffer,
                    color = finalColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // 2. 画纵向连线 (Wireframe) - 这一步让它变成“网格”而不是“圈圈”
                // 将当前环的点，与上一环的点连接起来
                if (prevProjectedPoints != null) {
                    for (j in 0 until segmentCount) {
                        val p1 = prevProjectedPoints!![j]
                        val p2 = projectedPoints[j]
                        drawLine(
                            color = finalColor.copy(alpha = finalColor.alpha * 0.5f), // 纵向线稍微暗一点
                            start = p1,
                            end = p2,
                            strokeWidth = strokeWidth * 0.5f
                        )
                    }
                }

                // 保存当前点供下一环使用
                prevProjectedPoints = projectedPoints
            }
            
            // 中心准星
            if (isWarping) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = 5f + Random.nextFloat() * 5f, // 抖动效果
                    center = Offset(centerX, centerY)
                )
            }
        }
    }
}

// 辅助：HSV 转 Color
private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val c = value * saturation
    val x = c * (1 - abs((hue / 60) % 2 - 1))
    val m = value - c
    val (r, g, b) = when {
        hue < 60 -> Triple(c, x, 0f)
        hue < 120 -> Triple(x, c, 0f)
        hue < 180 -> Triple(0f, c, x)
        hue < 240 -> Triple(0f, x, c)
        hue < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}