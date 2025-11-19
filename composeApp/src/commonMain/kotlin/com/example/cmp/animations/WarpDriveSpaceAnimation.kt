package com.example.cmp.animations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import kotlin.random.Random

// 星星的数据结构
class Star {
    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 0f
    var color: Color = Color.White
}

@Composable
fun WarpDriveSpaceAnimation(modifier: Modifier = Modifier) {
    // --- 配置 ---
    val starCount = 1000 // 星星数量，越多越密集
    val maxDepth = 2000f // 宇宙深度

    // --- 状态 ---
    // 预先创建对象池，避免 GC 卡顿
    val stars = remember {
        List(starCount) {
            Star().apply { reset(maxDepth, true) }
        }
    }

    var speed by remember { mutableStateOf(10f) } // 初始速度
    var isWarping by remember { mutableStateOf(false) } // 是否处于“光速模式”
    var centerX by remember { mutableStateOf(0f) }
    var centerY by remember { mutableStateOf(0f) }

    var frameTrigger by remember { mutableStateOf(0L) }

    // --- 动画循环 ---
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nanos ->
                frameTrigger = nanos

                // 目标速度：按住时加速到 100，松开回到 10
                val targetSpeed = if (isWarping) 200f else 5f
                // 简单的缓动效果 (Lerp)
                speed += (targetSpeed - speed) * 0.05f

                stars.forEach { star ->
                    // 核心逻辑：星星沿着 Z 轴向你飞来
                    star.z -= speed

                    // 如果星星跑到屏幕后面去了 (z <= 0)，就重置到最远处
                    if (star.z <= 1f) {
                        star.reset(maxDepth, false)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isWarping = true // 按下：进入光速
                        tryAwaitRelease()
                        isWarping = false // 松开：恢复巡航
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
            frameTrigger

            centerX = size.width / 2
            centerY = size.height / 2

            // 绘制每颗星星
            stars.forEach { star ->
                // --- 3D 投影核心公式 ---
                // x / z 实现了 "近大远小" 的透视效果
                // 乘以 800f 是为了放大视野 (FOV)
                val sx = (star.x / star.z) * 800f + centerX
                val sy = (star.y / star.z) * 800f + centerY

                // 计算这颗星星"上一帧"的位置，用来画拖尾线条
                // 速度越快，拖尾越长
                val prevZ = star.z + speed
                val px = (star.x / prevZ) * 800f + centerX
                val py = (star.y / prevZ) * 800f + centerY

                // 只有当星星在屏幕内时才绘制
                if (sx in 0f..size.width && sy in 0f..size.height) {

                    // 计算这一帧的线条粗细：越近越粗
                    val strokeWidth = (1f - star.z / maxDepth) * 5f

                    // 颜色逻辑：高速时发蓝/白，低速时稍微暗淡
                    val alpha = (1f - star.z / maxDepth).coerceIn(0f, 1f)

                    // 绘制拖尾
                    drawLine(
                        color = star.color.copy(alpha = alpha),
                        start = Offset(px, py),
                        end = Offset(sx, sy),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 简单的中心准星，增加驾驶感
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.3f),
                radius = 10f,
                center = Offset(centerX, centerY)
            )
        }
    }
}

// 辅助：重置星星位置
fun Star.reset(maxDepth: Float, randomZ: Boolean) {
    // x, y 在 -1000 到 1000 之间随机分布
    x = (Random.nextFloat() - 0.5f) * 4000f
    y = (Random.nextFloat() - 0.5f) * 4000f

    // z 重置到远处
    // randomZ=true 表示初始化时随机分布，否则都在最远处生成
    z = if (randomZ) Random.nextFloat() * maxDepth else maxDepth

    // 随机颜色 (赛博朋克风)
    val colors = listOf(
        Color(0xFFFFFFFF), // 白
        Color(0xFF00F0FF), // 青
        Color(0xFF00F0FF),
        Color(0xFFFF0055)  // 偶尔来点红色的
    )
    color = colors.random()
}