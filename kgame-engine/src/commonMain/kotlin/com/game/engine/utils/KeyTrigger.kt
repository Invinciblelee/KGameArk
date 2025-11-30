package com.game.engine.utils

import androidx.collection.MutableLongIntMap
import androidx.compose.ui.input.key.Key
import com.game.engine.input.InputManager

/**
 * A simple key trigger. this will only trigger once per key press.
 */
object KeyTrigger {
    @PublishedApi
    internal val downKeys = MutableLongIntMap()

    inline fun check(input: InputManager, key: Key, block: () -> Unit) {
        val down = input.isKeyDown(key)
        if (down && downKeys[key.keyCode] != 1) block()
        downKeys[key.keyCode] = if (down) 1 else 0
    }
}