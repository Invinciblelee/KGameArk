package com.game.engine.input

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputScope
import com.game.ecs.injectables.ViewportTransform
import kotlinx.coroutines.Runnable

fun interface KeyInterceptor {
     fun shouldIntercept(key: Key): Boolean

     companion object: KeyInterceptor {
         override fun shouldIntercept(key: Key): Boolean  {
             return when (key) {
                 Key.VolumeUp, Key.VolumeDown, Key.VolumeMute -> false
                 else -> true
             }
         }
     }
}

class InputManager(
    val viewportTransform: ViewportTransform,
    val interceptor: KeyInterceptor = KeyInterceptor,
) {
    private val keysDown = mutableSetOf<Key>()
    private val keysUpCur  = mutableSetOf<Key>()
    private val keysUpLast = mutableSetOf<Key>()

    var pointerPosition: Offset = Offset.Zero
        private set
    var isPointerDown: Boolean = false
        private set

    fun onKeyDown(key: Key) {
        Runnable {  }
        keysDown.add(key)
    }

    fun onKeyUp(key: Key) {
        keysDown.remove(key)
        keysUpCur.add(key)
    }

    fun isKeyDown(key: Key): Boolean = key in keysDown

    fun isKeyUp(key: Key): Boolean = key in keysUpLast

    // 虚拟轴 (例如 AD 移动返回 -1/0/1)
    fun getAxis(positive: Key, negative: Key): Float {
        var axisValue = 0f
        if (isKeyDown(positive)) {
            axisValue += 1f
        }
        if (isKeyDown(negative)) {
            axisValue -= 1f
        }
        return axisValue
    }

    internal fun handleKeyEvent(event: KeyEvent): Boolean {
        when (event.type) {
            KeyEventType.KeyDown -> onKeyDown(event.key)
            KeyEventType.KeyUp -> onKeyUp(event.key)
        }
        return interceptor.shouldIntercept(event.key)
    }

    internal suspend fun handlePointerEvent(scope: PointerInputScope, canvasOffset: Offset) {
        scope.detectDragGestures(
            onDragStart = { position ->
                onPointerUpdate(position, canvasOffset, down = true)
            },
            onDragEnd = { onPointerUpdate(down = false) },
            onDragCancel = { onPointerUpdate(down = false) },
            onDrag = { change, _ ->
                change.consume()
                onPointerUpdate(change.position, canvasOffset, down = true)
            }
        )
    }

    private fun onPointerUpdate(position: Offset? = null, canvasOffset: Offset? = null, down: Boolean) {
        if (position != null) {
            val globalPosition = position + (canvasOffset ?: Offset.Zero)
            val virtualPosition = viewportTransform.actualToVirtual(globalPosition)
            pointerPosition = virtualPosition
        }
        isPointerDown = down
    }

    internal fun endFrame() {
        keysUpLast.clear()
        keysUpLast.addAll(keysUpCur)
        keysUpCur.clear()
    }

    internal fun clear() {
        keysDown.clear()
        keysUpCur.clear()
        keysUpLast.clear()
        pointerPosition = Offset.Zero
        isPointerDown = false
    }

}