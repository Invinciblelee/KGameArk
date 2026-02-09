package com.kgame.plugins.visuals.particles

import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.*

internal class OffsetArrayList(val capacity: Int) : AbstractList<Offset>() {
    private val data = FloatArray(capacity shl 1)
    override val size: Int get() = capacity
    override fun get(index: Int): Offset = Offset(data[index shl 1], data[(index shl 1) + 1])

    fun set(index: Int, x: Float, y: Float) {
        val base = index shl 1
        data[base] = x
        data[base + 1] = y
    }

    fun getRawX(index: Int): Float = data[index shl 1]
    fun getRawY(index: Int): Float = data[(index shl 1) + 1]
}

internal class ColorArrayList(val capacity: Int) : AbstractList<Color>() {
    private val data = IntArray(capacity)
    override val size: Int get() = capacity
    override fun get(index: Int): Color = Color(data[index])

    fun set(index: Int, colorArgb: Int) {
        data[index] = colorArgb
    }
}