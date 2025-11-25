package com.game.engine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.game.engine.math.angle
import kotlinx.coroutines.isActive

// 请确保您的项目中 Offset 有 angle() 和 getDistance() 扩展函数
// 如果没有，您可能需要自行添加或使用其他数学库

data class JoypadValue(
    val vector: Offset = Offset.Zero,
    val angle: Float = 0f,
    val strength: Float = 0f
)

@Composable
fun GameJoypad(
    onValue: (JoypadValue) -> Unit,
    onRelease: () -> Unit = {},
    modifier: Modifier = Modifier,
    radius: Dp = 90.dp,
    deadRadius: Dp = 12.dp,
    dotRadius: Dp = 24.dp,
    bgColor: Color = Color.Black.copy(alpha = 0.4f),
    dotColor: Color = Color.White,
) {
    val density = LocalDensity.current
    val pxRadius     = with(density) { radius.toPx() }
    val pxDead       = with(density) { deadRadius.toPx() }
    val pxDot        = with(density) { dotRadius.toPx() }

    var center  by remember { mutableStateOf(Offset.Zero) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var visible by remember { mutableStateOf(false) }

    val value = remember(center, pointer) {
        val vec      = pointer - center
        val len      = vec.getDistance()
        val inDead   = len <= pxDead
        val clampLen = len.coerceAtMost(pxRadius)
        val clampVec = if (len == 0f) Offset.Zero else vec * (clampLen / len)
        JoypadValue(
            vector = if (inDead) Offset.Zero else clampVec / pxRadius,
            angle = vec.angle(),
            strength = (len / pxRadius).coerceIn(0f, 1f)
        )
    }

    // 解决协程捕获旧值问题，获取最新的 value
    val currentJoypadValue by rememberUpdatedState(value)

    if (visible) {
        // 持续帧循环
        LaunchedEffect(Unit) {
            while (isActive) {
                withFrameNanos {
                    if (currentJoypadValue.strength > 0f) {
                        onValue(currentJoypadValue)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        center  = down.position
                        pointer = down.position
                        visible = true

                        drag(down.id) { change ->
                            pointer = change.position
                            change.consume()
                        }

                        visible = false
                        onRelease()
                    }
                }
            }
    ) {
        if (visible) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = bgColor,
                    radius = pxRadius,
                    center = center
                )
                drawCircle(
                    color = dotColor,
                    radius = pxDot,
                    center = center + value.vector * pxRadius
                )
            }
        }
    }
}