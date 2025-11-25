package com.game.engine.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive

fun Modifier.pointerTickWhilePressed(
    onTick: () -> Unit
) = composed {
    var pressed by remember { mutableStateOf(false) }

    if (pressed) {
        LaunchedEffect(Unit) {
            while (isActive) {
                withFrameNanos { onTick() }
            }
        }
    }

    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                pressed = when (event.type) {
                    PointerEventType.Press, PointerEventType.Enter -> true
                    PointerEventType.Release, PointerEventType.Exit, PointerEventType.Unknown -> false
                    else -> pressed
                }
            }
        }
    }
}