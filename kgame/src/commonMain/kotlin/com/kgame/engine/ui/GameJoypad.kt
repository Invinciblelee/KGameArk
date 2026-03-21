package com.kgame.engine.ui

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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.Key.Companion.L
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kgame.engine.geometry.angleDegrees
import com.kgame.engine.input.InputManager
import com.kgame.engine.log.Logger
import kotlinx.coroutines.isActive

data class JoypadValue(
    val vector: Offset = Offset.Zero,
    val angle: Float = 0f,
    val strength: Float = 0f
)

/**
 * Processes joypad input and translates analog angles into discrete directional signals.
 * This implementation supports diagonal movement by evaluating X and Y axes independently.
 *
 * Sector Mapping (Center ± 67.5°):
 * - Right: [0, 67.5] ∪ [292.5, 360]
 * - Down:  [22.5, 157.5]
 * - Left:  [112.5, 247.5]
 * - Up:    [202.5, 337.5]
 */
fun InputManager.applyJoypad(value: JoypadValue) {
    // Dead-zone check: prevent stick drift from triggering unintended movement
    if (value.strength < 0.2f) return

    val angle = value.angle
    // Normalize angle to the standard [0, 360) range
    val normalizedAngle = (angle % 360f + 360f) % 360f

    /**
     * 1. Horizontal Axis Detection (X-Axis)
     * Note: Right direction spans across the 0/360 degree boundary.
     */
    val isRight = normalizedAngle in 0f..67.5f || normalizedAngle in 292.5f..360f
    val isLeft = normalizedAngle in 112.5f..247.5f

    /**
     * 2. Vertical Axis Detection (Y-Axis)
     */
    val isDown = normalizedAngle in 22.5f..157.5f
    val isUp = normalizedAngle in 202.5f..337.5f

    // 3. Trigger Key Simulation
    // Using independent IF blocks to allow simultaneous axis triggers (Diagonal movement)
    if (isRight) {
        simulateKey(Key.DirectionRight)
    }
    if (isLeft) {
        simulateKey(Key.DirectionLeft)
    }
    if (isDown) {
        simulateKey(Key.DirectionDown)
    }
    if (isUp) {
        simulateKey(Key.DirectionUp)
    }
}

@Composable
fun GameJoypad(
    onValue: (JoypadValue) -> Unit,
    onRelease: () -> Unit = {},
    modifier: Modifier = Modifier,
    radius: Dp = 48.dp,
    deadRadius: Dp = 0.dp,
    dotRadius: Dp = 24.dp,
    bgColor: Color = Color.Black.copy(alpha = 0.4f),
    dotColor: Color = Color.LightGray,
) {
    val density = LocalDensity.current
    val pxRadius = with(density) { radius.toPx() }
    val pxDead = with(density) { deadRadius.toPx() }
    val pxDot = with(density) { dotRadius.toPx() }

    var center by remember { mutableStateOf(Offset.Zero) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var visible by remember { mutableStateOf(false) }

    val value = remember(center, pointer) {
        val vec = pointer - center
        val len = vec.getDistance()
        val inDead = len <= pxDead
        val clampLen = len.coerceAtMost(pxRadius)
        val clampVec = if (len == 0f) Offset.Zero else vec * (clampLen / len)
        JoypadValue(
            vector = if (inDead) Offset.Zero else clampVec / pxRadius,
            angle = vec.angleDegrees(),
            strength = (len / pxRadius).coerceIn(0f, 1f)
        )
    }

    val currentJoypadValue by rememberUpdatedState(value)

    if (visible) {
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
                        center = down.position
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