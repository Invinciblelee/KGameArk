package com.example.cmp.animations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// 数据类保持不变
data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float = 1.0f,
    var color: Color,
    var size: Float
)

@Composable
fun CoolParticleAnimation(modifier: Modifier = Modifier) {
    // 【核心优化 1】使用普通的 MutableList，而不是 mutableStateListOf
    // 这完全避开了 Compose 状态系统的开销
    val particles = remember { mutableListOf<Particle>() }

    // 【核心优化 2】创建一个“触发器”状态，仅仅为了告诉 Canvas "该画下一帧了"
    var frameTrigger by remember { mutableStateOf(0L) }

    // 触摸位置状态（这个依然需要是 State，因为 UI 交互需要它）
    var touchPosition by remember { mutableStateOf<Offset?>(null) }

    var hue by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nanos ->
                // 更新触发器，这会导致读取了 frameTrigger 的 Canvas 重绘
                frameTrigger = nanos

                hue = (hue + 0.5f) % 360f

                // 逻辑与之前相同，但现在操作的是普通 List，速度极快
                touchPosition?.let { pos ->
                    repeat(5) {
                        val angle = Random.nextFloat() * 2 * PI
                        val speed = Random.nextFloat() * 5 + 2
                        particles.add(
                            Particle(
                                x = pos.x,
                                y = pos.y,
                                vx = (cos(angle) * speed).toFloat(),
                                vy = (sin(angle) * speed).toFloat(),
                                color = hsvToColor(hue, 1f, 1f),
                                size = Random.nextFloat() * 10 + 5
                            )
                        )
                    }
                }

                val iterator = particles.listIterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    p.x += p.vx
                    p.y += p.vy
                    p.vy += 0.2f
                    p.vx *= 0.96f
                    p.vy *= 0.96f
                    p.life -= 0.015f
                    p.size *= 0.95f

                    if (p.life <= 0f || p.size < 0.5f) {
                        iterator.remove()
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
                    onDragStart = { touchPosition = it },
                    onDragEnd = { touchPosition = null },
                    onDragCancel = { touchPosition = null },
                    onDrag = { change, _ -> touchPosition = change.position }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        touchPosition = offset
                        tryAwaitRelease()
                        touchPosition = null
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
            // 【关键点】读取 frameTrigger。
            // 虽然我们没有用到这个变量的值，但读取它会告诉 Compose：
            // "每当 frameTrigger 变化时，请重新运行这个 DrawScope"
            frameTrigger

            // 此时绘制普通的 particles 列表，不会触发状态系统检查
            particles.forEach { p ->
                drawCircle(
                    color = p.color.copy(alpha = p.life),
                    radius = p.size,
                    center = Offset(p.x, p.y),
                    blendMode = BlendMode.Plus
                )
            }
        }
    }
}

// 辅助函数
private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val c = value * saturation
    val x = c * (1 - kotlin.math.abs((hue / 60) % 2 - 1))
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