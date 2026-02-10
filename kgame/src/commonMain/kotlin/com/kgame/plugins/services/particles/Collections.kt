package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color


internal abstract class SizedList<T>(val capacity: Int) : AbstractList<T>() {
    private var _logicalSize: Int = 0

    override val size: Int get() = _logicalSize

    open fun limit(newSize: Int): SizedList<T> {
        _logicalSize = newSize.coerceIn(0, capacity)
        return this
    }
}

internal class OffsetArrayList(capacity: Int) : SizedList<Offset>(capacity) {
    private var data = FloatArray(512 shl 1)
    private var internalCapacity = 512

    override fun limit(newSize: Int): SizedList<Offset> {
        val safeSize = newSize.coerceIn(0, capacity)
        if (safeSize > internalCapacity) {
            ensureCapacity(safeSize)
        }
        super.limit(safeSize)
        return this
    }

    override fun get(index: Int): Offset {
        return Offset(data[index shl 1], data[(index shl 1) + 1])
    }

    fun set(index: Int, x: Float, y: Float) {
        if (index >= internalCapacity) ensureCapacity(index + 1)
        val base = index shl 1
        data[base] = x
        data[base + 1] = y
    }

    private fun ensureCapacity(minSize: Int) {
        var newCap = internalCapacity
        while (newCap < minSize) {
            newCap = (newCap * 1.5f).toInt().coerceAtMost(capacity)
            if (newCap == internalCapacity) break
        }
        data = data.copyOf(newCap shl 1)
        internalCapacity = newCap
    }

    fun getX(index: Int): Float = data[index shl 1]
    fun getY(index: Int): Float = data[(index shl 1) + 1]
}

internal class ColorArrayList(capacity: Int) : SizedList<Color>(capacity) {
    private var data = IntArray(512)
    private var internalCapacity = 512

    override fun get(index: Int): Color {
        return Color(data[index])
    }

    fun set(index: Int, argb: Int) {
        if (index >= internalCapacity) ensureCapacity(index + 1)
        data[index] = argb
    }

    private fun ensureCapacity(minSize: Int) {
        var newCap = internalCapacity
        while (newCap < minSize) {
            newCap = (newCap * 1.5f).toInt().coerceAtMost(capacity)
            if (newCap == internalCapacity) break
        }
        data = data.copyOf(newCap)
        internalCapacity = newCap
    }

    fun getArgb(index: Int): Int = data[index]
}