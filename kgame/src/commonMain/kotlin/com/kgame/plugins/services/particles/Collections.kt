package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

internal abstract class SizedList<T>(val capacity: Int) : AbstractList<T>() {
    protected var logicalSize: Int = 0
    protected var limitSize: Int? = null

    // Priority logic: Use limitSize if available, otherwise fallback to logical progress
    override val size: Int get() = limitSize ?: logicalSize

    open fun limit(newSize: Int): SizedList<T> {
        limitSize = newSize.coerceIn(0, capacity)
        return this
    }
}

internal class OffsetArrayList(capacity: Int) : SizedList<Offset>(capacity) {
    private var data = FloatArray(512 shl 1)
    private var internalCapacity = 512

    override fun limit(newSize: Int): OffsetArrayList {
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

    /**
     * Set coordinates and automatically track logicalSize.
     * Manual limit() will still override the final size get().
     */
    fun set(index: Int, x: Float, y: Float) {
        if (index >= internalCapacity) ensureCapacity(index + 1)
        val base = index shl 1
        data[base] = x
        data[base + 1] = y

        // Track the physical high-water mark
        if (index >= logicalSize) {
            logicalSize = (index + 1).coerceAtMost(capacity)
        }
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
        return Color(getArgb(index))
    }

    /**
     * Set ARGB and automatically track logicalSize.
     */
    fun set(index: Int, argb: Int) {
        if (index >= internalCapacity) ensureCapacity(index + 1)
        data[index] = argb

        if (index >= logicalSize) {
            logicalSize = (index + 1).coerceAtMost(capacity)
        }
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

    fun getArgb(index: Int): Int {
        return data[index]
    }
}

internal class ArraySlotMap(var capacity: Int = 16) {
    private var keys = IntArray(capacity)
    private var values = LongArray(capacity) // Store everything as 64-bit bits
    private var size = 0

    // --- Double Ops ---
    fun setDouble(key: Int, value: Double) = setRaw(key, value.toRawBits())
    fun getDouble(key: Int): Double = Double.fromBits(getRaw(key))

    // --- Float Ops ---
    fun setFloat(key: Int, value: Float) = setRaw(key, value.toRawBits().toLong())
    fun getFloat(key: Int): Float = Float.fromBits(getRaw(key).toInt())

    // --- Long Ops ---
    fun setLong(key: Int, value: Long) = setRaw(key, value)
    fun getLong(key: Int): Long = getRaw(key)

    // --- Int Ops ---
    fun setInt(key: Int, value: Int) = setRaw(key, value.toLong() and 0xFFFFFFFFL)
    fun getInt(key: Int): Int = getRaw(key).toInt()

    private fun setRaw(key: Int, rawBits: Long) {
        val index = findIndex(key)
        if (index != -1) {
            values[index] = rawBits
            return
        }
        if (size >= capacity) grow()
        keys[size] = key
        values[size] = rawBits
        size++
    }

    private fun getRaw(key: Int): Long {
        val index = findIndex(key)
        return if (index != -1) values[index] else 0L
    }

    private fun findIndex(key: Int): Int {
        var i = 0
        while (i < size) {
            if (keys[i] == key) return i
            i++
        }
        return -1
    }

    private fun grow() {
        capacity = capacity shl 1
        keys = keys.copyOf(capacity)
        values = values.copyOf(capacity)
    }
}