package com.example.cmp.animations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import kotlin.math.hypot
import kotlin.math.sqrt

// 1. 物理节点
class VPoint(
    var x: Float,
    var y: Float
) {
    // 记录上一帧的位置，通过 (x - oldX) 隐式计算速度，无需存储 velocity 变量
    var oldX: Float = x
    var oldY: Float = y
    
    // 是否被“钉”在原地（比如边缘的点）
    var isPinned: Boolean = false
}

// 2. 约束（弹簧/棍子）
class VStick(
    val p1: VPoint,
    val p2: VPoint,
    var length: Float // 目标距离
)

@Composable
fun NeuralElasticGridAnimation(modifier: Modifier = Modifier) {
    // --- 配置参数 ---
    val rows = 25       // 网格行数
    val cols = 15       // 网格列数
    val stiffness = 5   // 硬度 (迭代次数，越高越硬，消耗越大)
    val friction = 0.98f // 摩擦力 (0.9 ~ 0.99)
    val dragRadius = 150f // 手指排斥范围
    
    // --- 状态 ---
    var points by remember { mutableStateOf<List<VPoint>>(emptyList()) }
    var sticks by remember { mutableStateOf<List<VStick>>(emptyList()) }
    var screenSize by remember { mutableStateOf(Offset.Zero) }
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    var frameTrigger by remember { mutableStateOf(0L) }

    // --- 初始化物理世界 ---
    LaunchedEffect(screenSize) {
        if (screenSize.x > 0) {
            val gapX = screenSize.x / (cols - 1)
            val gapY = screenSize.y / (rows - 1)

            val newPoints = ArrayList<VPoint>()
            val newSticks = ArrayList<VStick>()

            // 创建点阵
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val p = VPoint(c * gapX, r * gapY)
                    // 把四周边缘钉死，形成一个框，中间是软的
                    if (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) {
                        p.isPinned = true
                    }
                    newPoints.add(p)
                }
            }

            // 创建约束（横向和纵向连接）
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val currentIdx = r * cols + c
                    val p = newPoints[currentIdx]

                    // 向右连
                    if (c < cols - 1) {
                        val right = newPoints[currentIdx + 1]
                        newSticks.add(VStick(p, right, gapX))
                    }
                    // 向下连
                    if (r < rows - 1) {
                        val down = newPoints[currentIdx + cols]
                        newSticks.add(VStick(p, down, gapY))
                    }
                }
            }
            
            points = newPoints
            sticks = newSticks
        }
    }

    // --- 物理引擎循环 ---
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nanos ->
                frameTrigger = nanos
                
                val currentPoints = points // 局部引用
                if (currentPoints.isEmpty()) return@withFrameNanos

                // A. 更新点的位置 (Verlet Integration)
                // x = x + (x - oldX) * friction
                for (p in currentPoints) {
                    if (!p.isPinned) {
                        val vx = (p.x - p.oldX) * friction
                        val vy = (p.y - p.oldY) * friction
                        
                        p.oldX = p.x
                        p.oldY = p.y
                        p.x += vx
                        p.y += vy
                    }
                }

                // B. 处理交互 (手指斥力场)
                touchPos?.let { touch ->
                    for (p in currentPoints) {
                        if (!p.isPinned) {
                            val dx = p.x - touch.x
                            val dy = p.y - touch.y
                            val distSq = dx * dx + dy * dy
                            
                            // 如果点在手指范围内，被推开
                            if (distSq < dragRadius * dragRadius && distSq > 0) {
                                val dist = sqrt(distSq)
                                val force = (dragRadius - dist) / dragRadius // 0~1 越近力越大
                                val pushX = (dx / dist) * force * 30f // 30f 是斥力强度
                                val pushY = (dy / dist) * force * 30f
                                
                                p.x += pushX
                                p.y += pushY
                            }
                        }
                    }
                }

                // C. 满足约束 (解算弹簧)
                // 迭代多次以增加刚性
                repeat(stiffness) {
                    for (s in sticks) {
                        val dx = s.p2.x - s.p1.x
                        val dy = s.p2.y - s.p1.y
                        val dist = hypot(dx, dy)
                        val difference = s.length - dist
                        val percent = difference / dist / 2f
                        val offsetX = dx * percent
                        val offsetY = dy * percent

                        if (!s.p1.isPinned) {
                            s.p1.x -= offsetX
                            s.p1.y -= offsetY
                        }
                        if (!s.p2.isPinned) {
                            s.p2.x += offsetX
                            s.p2.y += offsetY
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF050510)) // 深空黑背景
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
        Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
            if (size.width != screenSize.x || size.height != screenSize.y) {
                screenSize = Offset(size.width, size.height)
            }
            val tick = frameTrigger // 驱动重绘

            // 绘制线条
            // 性能优化：不创建 Path，直接画线，对于 25x15 这种量级非常快
            sticks.forEach { s ->
                val dx = s.p1.x - s.p2.x
                val dy = s.p1.y - s.p2.y
                // 简单的距离计算用于颜色
                val currentDist = dx * dx + dy * dy
                val targetDist = s.length * s.length
                
                // 计算张力比例：拉伸越长，张力越大
                // 阈值放宽一点以免闪烁
                val tension = (currentDist / targetDist - 1f).coerceIn(0f, 1f) 

                // 颜色插值：静止(紫) -> 拉伸(青/白)
                val lineColor = if (tension > 0.1f) {
                     androidx.compose.ui.graphics.lerp(
                         Color(0xFF4A00E0), // Purple
                         Color(0xFF00F0FF), // Cyan
                         tension * 2f // 增加一点灵敏度
                     )
                } else {
                    Color(0xFF4A00E0).copy(alpha = 0.4f) // 静止时暗淡一点
                }
                
                // 只有当有张力或者是交互状态时，才画粗线，否则画细线
                val strokeWidth = if(tension > 0.2f) 3f else 1.5f

                drawLine(
                    color = lineColor,
                    start = Offset(s.p1.x, s.p1.y),
                    end = Offset(s.p2.x, s.p2.y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            
            // 绘制节点（可选，画出来更有科技感）
            points.forEach { p ->
                // 只绘制内部点，不绘制边缘钉死的点
                if (!p.isPinned) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.3f),
                        radius = 1.5f,
                        center = Offset(p.x, p.y)
                    )
                }
            }
        }
    }
}