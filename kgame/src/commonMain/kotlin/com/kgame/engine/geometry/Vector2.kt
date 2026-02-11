package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.unpackFloat1
import androidx.compose.ui.util.unpackFloat2
import kotlin.jvm.JvmInline

@JvmInline
internal value class Vector2(val packedValue: Long) {
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