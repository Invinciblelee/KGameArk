package com.game.engine.input

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key

class InputManager {
    private val keysPressed = mutableSetOf<Key>()

    // 鼠标位置 (Compose State)
    var mousePosition by mutableStateOf(Offset.Zero)
        private set

    var isMouseDown by mutableStateOf(false)
        private set

    // --- 内部调用 (由 KGame Composable 喂数据) ---
    fun onKeyDown(key: Key) { keysPressed.add(key) }
    fun onKeyUp(key: Key) { keysPressed.remove(key) }

    fun onMouseUpdate(position: Offset, down: Boolean) {
        mousePosition = position
        isMouseDown = down
    }
    
    fun onMouseMove(position: Offset) {
        mousePosition = position
    }

    // --- 外部调用 (游戏逻辑使用) ---
    fun isKeyDown(key: Key): Boolean = key in keysPressed

    // 虚拟轴 (例如 AD 移动返回 -1/0/1)
    fun getAxis(positive: Key, negative: Key): Float {
        if (isKeyDown(positive)) return 1f
        if (isKeyDown(negative)) return -1f
        return 0f
    }
}