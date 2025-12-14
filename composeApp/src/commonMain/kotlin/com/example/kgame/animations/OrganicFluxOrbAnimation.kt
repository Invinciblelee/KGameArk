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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import kotlin.math.*

@Composable
fun OrganicFluxOrbAnimation(modifier: Modifier = Modifier) {
    val pointCount = 20

    var time by remember { mutableStateOf(0f) }
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    val path = remember { Path() }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nanos ->
                time = nanos / 400_000_000f
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { touchPos = it },
                    onDragEnd = { touchPos = null },
                    onDragCancel = { touchPos = null },
                    onDrag = { change, _ -> touchPos = change.position }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val center = Offset(centerX, centerY)

            // 【核心修复】：基于屏幕最小边计算尺寸
            val minDimension = min(size.width, size.height)

            // 设定基础半径为屏幕宽度的 1/4 (即直径占屏幕的一半)
            // 留出 50% 的空间给变形和拖拽，防止超出
            val baseRadius = minDimension * 0.25f
            val distortionRange = minDimension * 0.05f // 变形幅度也按比例

            path.reset()

            val points = MutableList(pointCount) { i ->
                val angle = (i.toFloat() / pointCount) * 2 * PI

                val wave1 = sin(time + i * 0.5f) * distortionRange
                val wave2 = cos(time * 1.5f + i * 0.2f) * (distortionRange / 2)
                val wave3 = sin(time * 0.3f + angle * 3) * (distortionRange / 3)

                var r = baseRadius + wave1 + wave2 + wave3

                touchPos?.let { touch ->
                    val dx = touch.x - centerX
                    val dy = touch.y - centerY
                    val px = cos(angle).toFloat()
                    val py = sin(angle).toFloat()

                    val dot = (dx * px + dy * py) / baseRadius

                    // 【修复交互】：拖拽拉伸的长度也是动态的
                    // 最大拉伸不超过基础半径的 80%
                    if (dot > 0) {
                        r += dot * (baseRadius * 0.8f)
                    }
                }

                Offset(
                    x = (centerX + cos(angle) * r).toFloat(),
                    y = (centerY + sin(angle) * r).toFloat()
                )
            }

            if (points.isNotEmpty()) {
                val firstPoint = points[0]
                val startX = (points[pointCount - 1].x + firstPoint.x) / 2
                val startY = (points[pointCount - 1].y + firstPoint.y) / 2
                path.moveTo(startX, startY)

                for (i in 0 until pointCount) {
                    val current = points[i]
                    val next = points[(i + 1) % pointCount]
                    val endX = (current.x + next.x) / 2
                    val endY = (current.y + next.y) / 2
                    path.quadraticTo(current.x, current.y, endX, endY)
                }
            }

            rotate(degrees = time * 20f, pivot = center) {
                drawPath(
                    path = path,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF00F0FF),
                            Color(0xFF5200FF),
                            Color(0xFFFF0055),
                            Color(0xFF00F0FF)
                        ),
                        center = center
                    ),
                    style = Fill
                )
            }

            rotate(degrees = -time * 10f, pivot = center) {
                drawPath(
                    path = path,
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                        center = center,
                        radius = baseRadius * 2f // 光晕范围也动态化
                    ),
                    style = Stroke(width = minDimension * 0.005f) // 线条粗细也动态化
                )
            }
        }
    }
}