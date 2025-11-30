package com.game.engine.input

import androidx.collection.MutableLongLongMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.game.engine.geometry.ViewportTransform

fun interface KeyInterceptor {
    fun shouldIntercept(key: Key): Boolean

    companion object : KeyInterceptor {
        override fun shouldIntercept(key: Key): Boolean {
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

    fun isKeyPressed(key: Key): Boolean

    fun getAxis(positive: Key, negative: Key): Float
    fun endFrame()
    fun clear()
}

class DefaultInputManager(
    val viewportTransform: ViewportTransform
) : InputManager {

    private companion object {
        const val MASK_IS_DOWN: Long = 1L shl 0
        const val MASK_WAS_DOWN: Long = 1L shl 1
        const val MASK_PENDING_UP: Long = 1L shl 2
    }

    private val keyStates = MutableLongLongMap()

    private var keyInterceptor: KeyInterceptor = KeyInterceptor

    override var pointerPosition: Offset = Offset.Zero
        private set

    override var isPointerDown: Boolean = false
        private set

    override fun intercept(interceptor: KeyInterceptor) {
        keyInterceptor = interceptor
    }

    override fun simulateKey(key: Key) {
        val keyCode = key.keyCode
        val currentState = keyStates.getOrDefault(keyCode, 0L)
        keyStates.put(keyCode, currentState or MASK_IS_DOWN or MASK_PENDING_UP)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.key.keyCode
        val currentState = keyStates.getOrDefault(keyCode, 0L) and MASK_PENDING_UP.inv()

        when (event.type) {
            KeyEventType.KeyDown -> {
                keyStates.put(keyCode, currentState or MASK_IS_DOWN)
            }
            KeyEventType.KeyUp -> {
                val newState = (currentState and MASK_IS_DOWN.inv()) or MASK_WAS_DOWN
                keyStates.put(keyCode, newState)
            }
        }

        return keyInterceptor.shouldIntercept(event.key)
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

    override fun isKeyDown(key: Key): Boolean {
        val state = keyStates.getOrDefault(key.keyCode, 0L)
        return (state and MASK_IS_DOWN) != 0L
    }

    override fun isKeyUp(key: Key): Boolean {
        val state = keyStates.getOrDefault(key.keyCode, 0L)
        return (state and MASK_WAS_DOWN) != 0L && (state and MASK_IS_DOWN) == 0L
    }

    override fun isKeyPressed(key: Key): Boolean {
        val state = keyStates.getOrDefault(key.keyCode, 0L)
        return (state and MASK_WAS_DOWN) == 0L && (state and MASK_IS_DOWN) != 0L
    }

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
        keyStates.forEach { keyCode, state ->
            if ((state and MASK_PENDING_UP) != 0L) {
                var newState = (state and MASK_IS_DOWN.inv()) and MASK_PENDING_UP.inv()
                newState = newState or MASK_WAS_DOWN
                keyStates.put(keyCode, newState)
            } else {
                val nextState = if ((state and MASK_IS_DOWN) != 0L) {
                    (state or MASK_WAS_DOWN)
                } else {
                    (state and MASK_WAS_DOWN.inv())
                }
                keyStates.put(keyCode, nextState)
            }
        }
    }

    override fun clear() {
        keyStates.clear()
        pointerPosition = Offset.Zero
        isPointerDown = false
    }
}