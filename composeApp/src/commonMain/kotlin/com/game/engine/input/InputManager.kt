package com.game.engine.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.game.ecs.injectables.ViewportTransform

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

interface InputManager {
    val pointerPosition: Offset

    val isPointerDown: Boolean

    fun intercept(interceptor: KeyInterceptor)

    fun simulateKey(key: Key)

    fun onKeyEvent(event: KeyEvent): Boolean

    fun onPointerStart()

    fun onPointerUpdate(position: Offset, canvasOffset: Offset)

    fun onPointerEnd()

    fun isKeyDown(key: Key): Boolean

    fun isKeyUp(key: Key): Boolean

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

    fun endFrame()

    fun clear()

}

class DefaultInputManager(
    val viewportTransform: ViewportTransform
): InputManager {

    private val keysDown = mutableSetOf<Key>()
    private val keysUpCur  = mutableSetOf<Key>()
    private val keysUpLast = mutableSetOf<Key>()

    private val pendingUp = mutableSetOf<Key>()

    private var keyInterceptor: KeyInterceptor = KeyInterceptor

    override var pointerPosition: Offset = Offset.Zero
        private set

    override var isPointerDown: Boolean = false
        private set

    override fun intercept(interceptor: KeyInterceptor) {
        keyInterceptor = interceptor
    }

    override fun simulateKey(key: Key) {
        keysDown.add(key)
        pendingUp.add(key)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val key = event.key
        when (event.type) {
            KeyEventType.KeyDown -> keysDown.add(key)
            KeyEventType.KeyUp -> {
                keysDown.remove(key)
                keysUpCur.add(key)
            }
        }
        return keyInterceptor.shouldIntercept(key)
    }

    override fun onPointerStart() {
        isPointerDown = true
    }

    override fun onPointerUpdate(position: Offset, canvasOffset: Offset) {
        val globalPosition = position + canvasOffset
        val virtualPosition = viewportTransform.actualToVirtual(globalPosition)
        pointerPosition = virtualPosition
    }

    override fun onPointerEnd() {
        isPointerDown = false
    }

    override fun isKeyDown(key: Key): Boolean = key in keysDown

    override fun isKeyUp(key: Key): Boolean = key in keysUpLast

    // 虚拟轴 (例如 AD 移动返回 -1/0/1)
    override fun getAxis(positive: Key, negative: Key): Float {
        var axisValue = 0f
        if (isKeyDown(positive)) {
            axisValue += 1f
        }
        if (isKeyDown(negative)) {
            axisValue -= 1f
        }
        return axisValue
    }

    override fun endFrame() {
        keysUpCur.addAll(pendingUp)
        keysDown.removeAll(pendingUp)
        pendingUp.clear()

        keysUpLast.clear()
        keysUpLast.addAll(keysUpCur)
        keysUpCur.clear()
    }

    override fun clear() {
        keysDown.clear()
        keysUpCur.clear()
        keysUpLast.clear()
        pointerPosition = Offset.Zero
        isPointerDown = false
    }

}