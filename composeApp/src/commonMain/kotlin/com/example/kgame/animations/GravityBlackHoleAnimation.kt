package com.example.kgame.animations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

// 粒子实体
class GravityParticle {
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var color: Color = Color.White
    var size: Float = 0f

    // 重置粒子到屏幕边缘随机位置
    fun reset(screenWidth: Float, screenHeight: Float) {
        // 随机出现在屏幕四周，而不是中间
        if (Random.nextBoolean()) {
            x = if (Random.nextBoolean()) 0f else screenWidth
            y = Random.nextFloat() * screenHeight
        } else {
            x = Random.nextFloat() * screenWidth
            y = if (Random.nextBoolean()) 0f else screenHeight
        }
        
        // 给一个缓慢的初始漂移速度
        vx = (Random.nextFloat() - 0.5f) * 2f
        vy = (Random.nextFloat() - 0.5f) * 2f
        
        size = Random.nextFloat() * 3f + 1f
        
        // 颜色：青色、紫色、白色混合
        val colors = listOf(
            Color(0xFF64D2FF), // Cyan
            Color(0xFFBF5AF2), // Purple
            Color(0xFFFFFFFF)  // White
        )
        color = colors.random().copy(alpha = Random.nextFloat() * 0.5f + 0.5f)
    }
}

@Composable
fun GravityBlackHoleAnimation(modifier: Modifier) {
    // --- 配置 ---
    val particleCount = 800
    val blackHoleRadius = 40f // 黑洞本体半径
    val gravityStrength = 1500f // 引力强度
    
    // --- 状态 ---
    val particles = remember { List(particleCount) { GravityParticle() } }
    var screenSize by remember { mutableStateOf(Offset.Zero) }
    var touchPos by remember { mutableStateOf<Offset?>(null) } // 黑洞位置
    
    // 默认黑洞在屏幕中心
    var blackHolePos by remember { mutableStateOf(Offset.Zero) }
    
    // 必不可少的帧触发器
    var frameTrigger by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nanos ->
                frameTrigger = nanos
                
                val centerX = if (touchPos != null) touchPos!!.x else blackHolePos.x
                val centerY = if (touchPos != null) touchPos!!.y else blackHolePos.y
                
                // 平滑移动黑洞位置 (简单的插值动画)
                blackHolePos += Offset(
                    (centerX - blackHolePos.x) * 0.1f,
                    (centerY - blackHolePos.y) * 0.1f
                )

                particles.forEach { p ->
                    // 1. 计算粒子到黑洞的距离向量
                    val dx = blackHolePos.x - p.x
                    val dy = blackHolePos.y - p.y
                    val distSq = dx * dx + dy * dy
                    val dist = sqrt(distSq)

                    // 2. 应用引力 (F = G / r^2)
                    // 为了防止除以0爆炸，加一个最小距离限制
                    val safeDist = max(dist, blackHoleRadius)
                    val force = gravityStrength / safeDist 
                    
                    // 将力分解到 XY 轴
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force
                    
                    p.vx += fx * 0.05f // 施加加速度
                    p.vy += fy * 0.05f

                    // 3. 摩擦力 (模拟太空介质，防止速度无限增加)
                    p.vx *= 0.99f
                    p.vy *= 0.99f
                    
                    // 4. 更新位置
                    p.x += p.vx
                    p.y += p.vy

                    // 5. 事件视界判定 (Event Horizon)
                    // 如果粒子靠太近，被“吞噬”并重置
                    if (dist < blackHoleRadius * 0.8f) {
                        p.reset(screenSize.x, screenSize.y)
                    }
                    
                    // 如果粒子跑出屏幕太远，也重置
                    if (p.x < -100 || p.x > screenSize.x + 100 || 
                        p.y < -100 || p.y > screenSize.y + 100) {
                         // 只有当它背离黑洞远去时才重置，防止刚生成就被删掉
                         if (dist > max(screenSize.x, screenSize.y)) {
                             p.reset(screenSize.x, screenSize.y)
                         }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { touchPos = it },
                    onDragEnd = { touchPos = null }, // 松手后，黑洞会慢慢回到最后的位置
                    onDragCancel = { touchPos = null },
                    onDrag = { change, _ -> touchPos = change.position }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
            // 初始化屏幕尺寸和黑洞默认位置
            if (size.width != screenSize.x || size.height != screenSize.y) {
                screenSize = Offset(size.width, size.height)
                // 如果还没触摸过，默认在中心
                if (blackHolePos == Offset.Zero) {
                    blackHolePos = Offset(size.width / 2, size.height / 2)
                    // 初始化所有粒子
                    particles.forEach { it.reset(size.width, size.height) }
                }
            }
            
            frameTrigger // 激活重绘

            // 1. 绘制黑洞 (吸积盘光晕)
            // 使用径向渐变模拟发光中心
            val brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black,             // 中心是绝对黑
                    Color(0xFF401050),       // 内圈深紫
                    Color(0xFFFF9500),       // 吸积盘亮橙
                    Color.Transparent        // 外圈透明
                ),
                center = blackHolePos,
                radius = blackHoleRadius * 4f // 光晕范围
            )
            drawCircle(
                brush = brush,
                radius = blackHoleRadius * 4f,
                center = blackHolePos
            )
            
            // 绘制黑洞本体 (绝对黑体)
            drawCircle(
                color = Color.Black,
                radius = blackHoleRadius,
                center = blackHolePos
            )
            // 加上白色边框加强对比
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = blackHoleRadius,
                center = blackHolePos,
                style = Stroke(width = 1f)
            )

            // 2. 绘制粒子
            particles.forEach { p ->
                // 速度越快，粒子拉得越长 (Spaghettification 面条化效应)
                val speed = sqrt(p.vx * p.vx + p.vy * p.vy)
                val tailLength = (speed * 2f).coerceIn(0f, 30f)
                
                if (tailLength > 2f) {
                    // 高速：画线
                    drawLine(
                        color = p.color,
                        start = Offset(p.x, p.y),
                        end = Offset(p.x - p.vx * 2f, p.y - p.vy * 2f), // 反向拖尾
                        strokeWidth = p.size,
                        cap = StrokeCap.Round
                    )
                } else {
                    // 低速：画点
                    drawCircle(
                        color = p.color,
                        radius = p.size / 2,
                        center = Offset(p.x, p.y)
                    )
                }
            }
        }
    }
}