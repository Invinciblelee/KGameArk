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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.random.Random

// 预定义字符池数组，避免 String 操作
val CHAR_POOL_ARRAY = "ﾊﾐﾋｰｳｼﾅﾓﾆｻﾜﾂｵﾘｱﾎﾃﾏｹﾒｴｶｷﾑﾕﾗｾﾈｽﾀﾇﾍ1234567890ZXYW".toCharArray()

data class BatchColumn(
    val x: Float,
    var y: Float,
    var speed: Float,
    // 直接存储拼接好的 String，而不是 List<Char>
    var text: String,
    // 存储纯字符数组用于逻辑轮换
    val charBuffer: CharArray,
    var themeIndex: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BatchColumn

        if (x != other.x) return false
        if (y != other.y) return false
        if (speed != other.speed) return false
        if (themeIndex != other.themeIndex) return false
        if (text != other.text) return false
        if (!charBuffer.contentEquals(other.charBuffer)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + speed.hashCode()
        result = 31 * result + themeIndex
        result = 31 * result + text.hashCode()
        result = 31 * result + charBuffer.contentHashCode()
        return result
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun CyberpunkMatrixAnimation(modifier: Modifier = Modifier) {
    // 1. 配置
    val fontSize = 14.sp
    val tailLength = 18 // 尾巴长度

    val measurer = rememberTextMeasurer()
    // 测量单个字符高度
    val charSize = remember(measurer) {
        measurer.measure("0", TextStyle(fontSize = fontSize, fontFamily = FontFamily.Monospace)).size
    }
    val charWidth = charSize.width.toFloat()
    val charHeight = charSize.height.toFloat()

    // 2. 预创建渐变 Brush (关键优化)
    // 我们用 Brush 来实现“头亮尾暗”的效果，而不是一个个字设颜色
    val brushes = remember {
        val colors = listOf(
            listOf(Color.White, Color(0xFF00FF41), Color.Transparent), // Green
            listOf(Color.White, Color(0xFF00F0FF), Color.Transparent), // Blue
            listOf(Color.White, Color(0xFFFF00FF), Color.Transparent)  // Purple
        )
        colors.map { c ->
            // 从上到下的线性渐变
            Brush.verticalGradient(
                0.0f to c[0],
                0.1f to c[1], // 头部亮一点
                1.0f to c[2]  // 尾部透明
            )
        }
    }
    val glitchBrush = remember {
        Brush.verticalGradient(listOf(Color.White, Color.Red, Color.Transparent))
    }

    var columns by remember { mutableStateOf<List<BatchColumn>>(emptyList()) }
    var screenSize by remember { mutableStateOf(Offset.Zero) }
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    var frameTrigger by remember { mutableStateOf(0L) }

    // 3. 初始化
    LaunchedEffect(screenSize) {
        if (screenSize.x > 0) {
            val colCount = (screenSize.x / charWidth).toInt() + 1

            // 在 IO 线程做初始化，避免卡 UI
            withContext(Dispatchers.Default) {
                val newCols = List(colCount) { i ->
                    val buffer = CharArray(tailLength) { CHAR_POOL_ARRAY.random() }
                    BatchColumn(
                        x = i * charWidth,
                        y = Random.nextFloat() * -screenSize.y,
                        speed = Random.nextFloat() * 10f + 5f,
                        charBuffer = buffer,
                        text = buffer.joinToString("\n"), // 预先拼接
                        themeIndex = Random.nextInt(3)
                    )
                }
                columns = newCols
            }
        }
    }

    // 4. 逻辑循环
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nanos ->
                frameTrigger = nanos

                // 这里我们只更新每一列的 Y 坐标和偶尔更新文字内容
                // 尽量减少 String 的重新拼接频率
                columns.forEach { col ->
                    var currentSpeed = col.speed

                    // 交互：手指附近反而变慢 (时间停滞感)
                    touchPos?.let { t ->
                        if (abs(col.x - t.x) < 80f) currentSpeed = 0.5f
                    }

                    col.y += currentSpeed

                    if (Random.nextFloat() < 0.01f) {
                        val last = col.charBuffer[tailLength - 1]

                        // KMP 兼容的数组移位
                        col.charBuffer.copyInto(
                            destination = col.charBuffer,
                            destinationOffset = 1,
                            startIndex = 0,
                            endIndex = tailLength - 1
                        )

                        col.charBuffer[0] = if (Random.nextBoolean()) CHAR_POOL_ARRAY.random() else last
                        col.text = col.charBuffer.joinToString("\n")
                    }

                    // 循环重置逻辑
                    val colHeightPx = tailLength * charHeight
                    if (col.y - colHeightPx > screenSize.y) {
                        col.y = -Random.nextFloat() * 100f
                        col.speed = Random.nextFloat() * 3f + 2f
                        col.themeIndex = Random.nextInt(3)
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
                    onDragEnd = { touchPos = null },
                    onDragCancel = { touchPos = null },
                    onDrag = { change, _ -> touchPos = change.position }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
            if (size.width != screenSize.x || size.height != screenSize.y) {
                screenSize = Offset(size.width, size.height)
            }
            frameTrigger // 触发重绘

            val colHeightPx = tailLength * charHeight

            columns.forEach { col ->
                // 视锥剔除
                if (col.y > -charHeight && col.y - colHeightPx < size.height) {

                    val isGlitch = touchPos?.let { abs(col.x - it.x) < 40f } == true

                    val currentBrush = if (isGlitch) glitchBrush else brushes[col.themeIndex]

                    // 【核心改动】：一次绘制一整列！
                    drawText(
                        textMeasurer = measurer,
                        text = col.text,
                        topLeft = Offset(col.x, col.y - colHeightPx), // 这里的坐标计算要小心，确保是从上往下画
                        style = TextStyle(
                            fontSize = fontSize,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            // 直接把渐变 Brush 给文字，自动处理透明度
                            brush = currentBrush
                        )
                    )
                }
            }
        }
    }
}