package com.kgame.engine.geometry

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

    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
    operator fun times(other: Vector2) = Vector2(x * other.x, y * other.y)
    operator fun div(other: Vector2) = Vector2(
        if (other.x != 0f) x / other.x else 0f,
        if (other.y != 0f) y / other.y else 0f
    )
    operator fun times(scale: Float) = Vector2(x * scale, y * scale)

    companion object {
        val Zero = Vector2(0x0L)
    }
}

fun Vector2(x: Float, y: Float): Vector2 = Vector2(packFloats(x, y))