package com.game.engine.utils

import androidx.compose.ui.input.key.Key
import com.game.engine.input.InputManager

/**
 * A simple key trigger. this will only trigger once per key press.
 * @property key The key to trigger.
 */
class KeyTrigger(@PublishedApi internal val key: Key) {
    @PublishedApi
    internal var wasDown = false

    inline fun check(input: InputManager, block: () -> Unit) {
        val down = input.isKeyDown(key)
        if (down && !wasDown) block()
        wasDown = down
    }
}