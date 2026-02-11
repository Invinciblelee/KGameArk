package com.kgame.engine.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.packFloats
import androidx.compose.ui.util.unpackFloat1
import androidx.compose.ui.util.unpackFloat2
import kotlin.jvm.JvmInline

@JvmInline
value class Vector2(val packedValue: Long) {
    inline val x: Float
        get() = unpackFloat1(packedValue)

    inline val y: Float
        get() = unpackFloat2(packedValue)

    operator fun component1(): Float = x
    operator fun component2(): Float = y
    
    companion object {
        val Zero = Offset(0x0L)
    }
}

fun Vector2(x: Float, y: Float): Vector2 = Vector2(packFloats(x, y))