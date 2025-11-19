package com.example.cmp.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key.Companion.Window
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.isActive
import kotlin.math.hypot

// --- 1. 极简物理引擎 (Verlet Integration) ---

class VPoint(
    var x: Float,
    var y: Float,
    var oldX: Float = x,
    var oldY: Float = y,
    var isPinned: Boolean = false
)

class CenterRopePhysics(val segmentLength: Float = 10f, val pointCount: Int = 50) {
    val points = ArrayList<VPoint>()

    init {
        // 初始化
        for (i in 0 until pointCount) {
            points.add(VPoint(500f, 500f))
        }
    }

    fun update(rootPos: Offset, targetPos: Offset) {
        if (points.isEmpty()) return

        // --- 关键修改 ---

        // 1. 固定根部 (Root) -> 连在屏幕中心(主角)
        val root = points[0]
        root.x = rootPos.x
        root.y = rootPos.y
        root.isPinned = true

        // 2. 固定尾部 (Tip) -> 跟随鼠标
        // 如果你想要"甩鞭子"的感觉，可以不完全固定它，而是给它一个向鼠标的力
        // 但为了控制感，我们这里直接固定尾部
        val tip = points.last()
        tip.x = targetPos.x
        tip.y = targetPos.y
        tip.isPinned = true

        // 3. 物理模拟 (Verlet)
        for (i in 1 until points.size - 1) { // 头尾都固定了，只算中间的
            val p = points[i]

            // 惯性计算
            val vx = (p.x - p.oldX) * 0.90f // 阻力大一点，防止乱晃
            val vy = (p.y - p.oldY) * 0.90f

            p.oldX = p.x
            p.oldY = p.y

            p.x += vx
            p.y += vy

            // 移除重力，因为是俯视角
        }

        // 4. 约束求解 (让绳子保持长度)
        // 增加迭代次数，让绳子更"紧"一点
        repeat(20) {
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]

                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val dist = hypot(dx, dy)

                if (dist > 0) {
                    val difference = segmentLength - dist
                    val percent = difference / dist / 2f
                    val offsetX = dx * percent
                    val offsetY = dy * percent

                    if (!p1.isPinned) {
                        p1.x -= offsetX
                        p1.y -= offsetY
                    }
                    if (!p2.isPinned) {
                        p2.x += offsetX
                        p2.y += offsetY
                    }
                }
            }
        }
    }
}

// --- 2. 渲染界面 ---
@Composable
fun CoolGeometricSilk(modifier: Modifier = Modifier) {
    // 增加节点数量，让线更长、更软
    val rope = remember { CenterRopePhysics(segmentLength = 12f, pointCount = 60) }
    var mousePos by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos {
                // 逻辑更新交给 Draw 阶段或这里均可，这里只做触发
            }
        }
    }

    BoxWithConstraints(modifier = modifier.background(Color(0xFF101018))) {
        // 简单修正：直接取 Canvas 的 center
        var canvasCenter by remember { mutableStateOf(Offset.Zero) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { mousePos = it },
                        onDrag = { change, _ ->
                            change.consume()
                            mousePos = change.position
                        }
                    )
                }
        ) {
            canvasCenter = center

            // 如果鼠标没动过，默认放在稍微偏右的地方
            val target = if (mousePos == Offset.Zero) center + Offset(200f, 0f) else mousePos

            // 更新物理 (每帧调用)
            rope.update(canvasCenter, target)

            val points = rope.points
            if (points.isEmpty()) return@Canvas

            // 绘制贝塞尔曲线
            val path = Path()
            path.moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i+1]
                path.quadraticTo(p1.x, p1.y, (p1.x + p2.x) / 2, (p1.y + p2.y) / 2)
            }
            path.lineTo(points.last().x, points.last().y)

            // --- 渲染特效 ---

            // 1. 剑身光晕 (青色流光)
            drawPath(
                path = path,
                color = Color(0xFF00E5FF).copy(alpha = 0.3f),
                style = Stroke(width = 20f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 2. 剑身主体
            drawPath(
                path = path,
                color = Color(0xFF40C4FF),
                style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 3. 剑身核心 (高亮白线)
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 4. 绘制主角 (中心法阵)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFF00E5FF).copy(0.5f), Color.Transparent),
                    radius = 40f
                ),
                radius = 30f,
                center = canvasCenter
            )

            // 5. 绘制剑尖 (跟随鼠标)
            drawCircle(Color.White, 6f, points.last().let { Offset(it.x, it.y) })
            drawCircle(Color(0xFF00E5FF), 15f, points.last().let { Offset(it.x, it.y) }, style = Stroke(2f))
        }
    }
}