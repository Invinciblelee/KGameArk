package com.example.kgame.animations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import kotlin.math.*

data class RenderItem(
    val zIndex: Float, // 用于深度排序
    val drawAction: () -> Unit // 具体的绘制指令
)

@Composable
fun DigitalDNAHelixAnimation(modifier: Modifier = Modifier) {
    // --- 配置 ---
    val pairCount = 40         // 碱基对数量 (DNA 长度)
    val radius = 300f          // 螺旋半径
    val spacing = 40f          // 每一层的垂直间距
    val twist = 0.2f           // 扭曲程度 (控制螺旋的紧密)
    
    // --- 状态 ---
    // 旋转角度
    var angleY by remember { mutableStateOf(0f) } // 自转
    var angleX by remember { mutableStateOf(0.5f) } // 倾斜角
    
    // 交互控制速度
    var rotationSpeed by remember { mutableStateOf(0.02f) }
    var touchDrag by remember { mutableStateOf(Offset.Zero) }
    
    var frameTrigger by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nanos ->
                frameTrigger = nanos
                
                // 自动旋转
                angleY += rotationSpeed
                
                // 阻尼效果：如果松手，旋转速度慢慢回到默认值
                if (touchDrag == Offset.Zero) {
                    rotationSpeed = rotationSpeed * 0.95f + 0.02f * 0.05f
                    angleX = angleX * 0.95f + 0.5f * 0.05f // 回到默认倾斜
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(Brush.radialGradient(
                colors = listOf(Color(0xFF001E36), Color(0xFF000000)),
                radius = 1500f
            ))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { touchDrag = Offset.Zero },
                    onDragEnd = { touchDrag = Offset.Zero },
                    onDrag = { change, dragAmount ->
                        touchDrag = change.position
                        // 左右滑动控制旋转速度
                        rotationSpeed -= dragAmount.x * 0.0005f
                        // 上下滑动控制倾斜角度
                        angleX += dragAmount.y * 0.005f
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tick = frameTrigger
            val centerX = size.width / 2
            val centerY = size.height / 2
            
            // 渲染列表 (为了深度排序)
            val renderList = ArrayList<RenderItem>()

            // 循环生成每一层 DNA
            for (i in -pairCount / 2..pairCount / 2) {
                // 1. 计算原始 3D 坐标
                // 基础偏移 + 旋转偏移 + 呼吸动画 (sin(tick))
                val baseY = i * spacing
                val currentTwist = i * twist + angleY
                
                // 呼吸效果：让半径随时间微微膨胀
                val breathe = 1f + sin(tick / 500_000_000f) * 0.05f
                val currentRadius = radius * breathe

                // 链条 A 的位置
                val Ax = sin(currentTwist) * currentRadius
                val Az = cos(currentTwist) * currentRadius
                // 链条 B 的位置 (相差 PI，即 180度)
                val Bx = sin(currentTwist + PI.toFloat()) * currentRadius
                val Bz = cos(currentTwist + PI.toFloat()) * currentRadius

                // 2. 3D 旋转矩阵 (绕 X 轴倾斜)
                // y' = y*cos(θ) - z*sin(θ)
                // z' = y*sin(θ) + z*cos(θ)
                val cosX = cos(angleX)
                val sinX = sin(angleX)

                val Ay_rot = baseY * cosX - Az * sinX
                val Az_rot = baseY * sinX + Az * cosX
                
                val By_rot = baseY * cosX - Bz * sinX
                val Bz_rot = baseY * sinX + Bz * cosX

                // 3. 透视投影 (Perspective Projection)
                // fov (视野深度)
                val fov = 1000f
                val scaleA = fov / (fov + Az_rot)
                val scaleB = fov / (fov + Bz_rot)

                val ScreenAx = centerX + Ax * scaleA
                val ScreenAy = centerY + Ay_rot * scaleA
                
                val ScreenBx = centerX + Bx * scaleB
                val ScreenBy = centerY + By_rot * scaleB

                // 计算这一层的平均深度，用于决定谁遮挡谁
                val avgZ = (Az_rot + Bz_rot) / 2f

                // 4. 构建渲染指令
                renderList.add(RenderItem(zIndex = avgZ) {
                    // A. 绘制连接线 (Base Pair)
                    // 只有当两个点都在屏幕前才画
                    if (scaleA > 0 && scaleB > 0) {
                        val alpha = ((scaleA + scaleB) / 2f - 0.3f).coerceIn(0f, 1f) * 0.5f
                        drawLine(
                            color = Color(0xFF00F0FF).copy(alpha = alpha), // 赛博蓝
                            start = Offset(ScreenAx, ScreenAy),
                            end = Offset(ScreenBx, ScreenBy),
                            strokeWidth = 2f * ((scaleA + scaleB) / 2)
                        )
                    }

                    // B. 绘制节点 (Nucleotides)
                    // 节点 A
                    if (scaleA > 0) {
                        val alphaA = (scaleA - 0.2f).coerceIn(0.1f, 1f)
                        val colorA = if(i % 2 == 0) Color(0xFF00FF9D) else Color(0xFFFF0055) // 绿/粉交替
                        
                        drawCircle(
                            color = colorA.copy(alpha = alphaA),
                            radius = 8f * scaleA,
                            center = Offset(ScreenAx, ScreenAy)
                        )
                        // 发光光晕
                        drawCircle(
                            color = colorA.copy(alpha = alphaA * 0.3f),
                            radius = 15f * scaleA,
                            center = Offset(ScreenAx, ScreenAy)
                        )
                    }

                    // 节点 B
                    if (scaleB > 0) {
                        val alphaB = (scaleB - 0.2f).coerceIn(0.1f, 1f)
                        val colorB = if(i % 2 == 0) Color(0xFFFF0055) else Color(0xFF00FF9D) // 配对颜色
                        
                        drawCircle(
                            color = colorB.copy(alpha = alphaB),
                            radius = 8f * scaleB,
                            center = Offset(ScreenBx, ScreenBy)
                        )
                        // 发光光晕
                        drawCircle(
                            color = colorB.copy(alpha = alphaB * 0.3f),
                            radius = 15f * scaleB,
                            center = Offset(ScreenBx, ScreenBy)
                        )
                    }
                })
            }

            // 5. 深度排序 (Painter's Algorithm)
            // Z 值越大越远，Z 值越小越近。
            // 我们要先画远的 (Z 大)，再画近的 (Z 小)，所以按 Z 降序排列
            renderList.sortByDescending { it.zIndex }

            // 6. 执行绘制
            renderList.forEach { it.drawAction() }
        }
    }
}