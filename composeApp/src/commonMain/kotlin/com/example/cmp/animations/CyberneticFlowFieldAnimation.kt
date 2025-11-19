package com.example.cmp.animations

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

@Composable
fun CyberneticFlowFieldAnimation(modifier: Modifier = Modifier) {
    // --- 配置 ---
    val particleCount = 2000 // 粒子数量，性能好的手机可以开到 4000
    val speedBase = 2f       // 基础流速
    
    // --- 内存管理 (使用数组代替对象以提升性能) ---
    // format: [x, y]
    val positions = remember { FloatArray(particleCount * 2) } 
    // format: [vx, vy] - 记录之前的速度用于平滑
    val velocities = remember { FloatArray(particleCount * 2) }
    // format: [life] - 粒子生命周期 (0~1)
    val lifes = remember { FloatArray(particleCount) { Random.nextFloat() } }

    // 初始化位置
    var screenSize by remember { mutableStateOf(Offset.Zero) }
    var isInitialized by remember { mutableStateOf(false) }
    
    // 交互状态
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    var frameTrigger by remember { mutableStateOf(0L) }
    var time by remember { mutableStateOf(0f) }

    // --- 物理循环 ---
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nanos ->
                frameTrigger = nanos
                time += 0.005f // 时间推移，让场稍微动一动

                if (isInitialized) {
                    val width = screenSize.x
                    val height = screenSize.y
                    val obstacleRadius = 80f // 手指阻挡半径
                    val obstacleRadiusSq = obstacleRadius * obstacleRadius

                    for (i in 0 until particleCount) {
                        val ptr = i * 2 // 指针
                        var px = positions[ptr]
                        var py = positions[ptr + 1]

                        // 1. 计算当前位置的“流场角度” (伪噪声)
                        // 使用简单的三角函数叠加模拟 Perlin Noise
                        val noiseVal = sin(px / 150f + time) + cos(py / 150f - time) + sin((px + py) / 300f)
                        var angle = noiseVal * PI.toFloat()

                        // 2. 处理交互 (障碍物回避)
                        touchPos?.let { t ->
                            val dx = px - t.x
                            val dy = py - t.y
                            val distSq = dx * dx + dy * dy
                            
                            // 如果粒子撞到了手指的“力场”
                            if (distSq < obstacleRadiusSq) {
                                // 计算回避角度：切线方向
                                val dist = sqrt(distSq)
                                val normalX = dx / dist
                                val normalY = dy / dist
                                
                                // 强行推开
                                px += normalX * 5f
                                py += normalY * 5f
                                
                                // 修改流向，使其绕着手指转
                                // 叉乘计算切线
                                angle = atan2(normalY, normalX) + PI.toFloat() / 2
                            } else if (distSq < obstacleRadiusSq * 4) {
                                // 稍微远一点的地方受到扰动
                                angle += 100f / distSq 
                            }
                        }

                        // 3. 物理移动
                        val vx = cos(angle) * speedBase
                        val vy = sin(angle) * speedBase
                        
                        // 惯性混合 (让转弯更平滑)
                        velocities[ptr] = velocities[ptr] * 0.9f + vx * 0.1f
                        velocities[ptr + 1] = velocities[ptr + 1] * 0.9f + vy * 0.1f
                        
                        px += velocities[ptr]
                        py += velocities[ptr + 1]
                        
                        // 4. 边界循环 (Teleport)
                        if (px < 0) px += width
                        else if (px > width) px -= width
                        
                        if (py < 0) py += height
                        else if (py > height) py -= height

                        // 写回数组
                        positions[ptr] = px
                        positions[ptr + 1] = py
                        
                        // 更新生命周期 (用于闪烁效果)
                        lifes[i] -= 0.005f
                        if (lifes[i] <= 0) {
                            lifes[i] = 1f
                            // 重生时随机瞬移一下，避免死循环轨迹
                            positions[ptr] = Random.nextFloat() * width
                            positions[ptr + 1] = Random.nextFloat() * height
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black) // 深色背景
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { touchPos = it },
                    onDragEnd = { touchPos = null },
                    onDragCancel = { touchPos = null },
                    onDrag = { change, _ -> touchPos = change.position }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        touchPos = it
                        tryAwaitRelease()
                        touchPos = null
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 初始化
            if (!isInitialized && size.width > 0) {
                screenSize = Offset(size.width, size.height)
                for (i in 0 until particleCount) {
                    positions[i * 2] = Random.nextFloat() * size.width
                    positions[i * 2 + 1] = Random.nextFloat() * size.height
                }
                isInitialized = true
            }
            
            frameTrigger // 触发重绘

            // 绘制
            // 技巧：我们不通过 drawLine 画线，而是画“很短的线段”或者点
            // 为了视觉效果，我们画尾迹
            
            for (i in 0 until particleCount) {
                val ptr = i * 2
                val x = positions[ptr]
                val y = positions[ptr + 1]
                val vx = velocities[ptr]
                val vy = velocities[ptr + 1]
                val life = lifes[i]

                // 速度越快，颜色越亮
                val speed = sqrt(vx*vx + vy*vy)
                val speedFactor = (speed / speedBase).coerceIn(0f, 2f)
                
                // 交互时变色：如果在手指附近，变红/橙，否则是蓝/青
                var isNearTouch = false
                touchPos?.let { 
                    val dx = x - it.x
                    val dy = y - it.y
                    if (dx*dx + dy*dy < 150*150) isNearTouch = true
                }

                val color = if (isNearTouch) {
                    // 警告色
                    Color(0xFFFF5500).copy(alpha = life * 0.8f)
                } else {
                    // 赛博流光色 (根据位置和速度混合)
                    // 蓝色基调 + 速度带来的白色高光
                    Color(
                        red = 0.2f * speedFactor,
                        green = 0.8f * speedFactor, 
                        blue = 1f, 
                        alpha = life * 0.6f // 随机闪烁
                    )
                }

                // 绘制粒子拖尾
                // 利用当前速度反推上一帧位置，画一条短线
                drawLine(
                    color = color,
                    start = Offset(x, y),
                    end = Offset(x - vx * 3f, y - vy * 3f), // 尾巴长度系数
                    strokeWidth = if (isNearTouch) 2f else 1.5f,
                    cap = StrokeCap.Round
                )
            }
            
            // 绘制手指位置的力场光圈
            touchPos?.let {
                drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    radius = 80f,
                    center = it
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = 80f,
                    center = it,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                )
            }
        }
    }
}