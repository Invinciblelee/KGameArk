package com.game.engine.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key

class InputManager {
    private val keysDown = mutableSetOf<Key>()
    private val keysPressed = mutableSetOf<Key>()

    var pointerPosition: Offset = Offset.Zero
        private set
    var isPointerDown: Boolean = false
        private set

    fun onKeyDown(key: Key) {
        keysDown.add(key)
        keysPressed.clear()
    }
    fun onKeyUp(key: Key) {
        if (keysDown.remove(key)) {
            keysPressed.add(key)
        }
    }

    fun isKeyDown(key: Key): Boolean = key in keysDown

    fun isKeyPressed(key: Key): Boolean = keysPressed.remove(key)

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

    internal fun onPointerUpdate(position: Offset? = null, down: Boolean) {
        if (position != null) {
            pointerPosition = position
        }
        isPointerDown = down
    }

    internal fun clear() {
        keysDown.clear()
        keysPressed.clear()
        pointerPosition = Offset.Zero
        isPointerDown = false
    }

}