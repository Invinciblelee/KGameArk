package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * A high-performance, thread-safe context for managing particle system parameters.
 * It uses bit-packing for complex types (Offset, Size, Color) and delegates to
 * a 64-bit primitive container to avoid object boxing.
 */
class ParticleContext(val capacity: Int = 8) {
    // Shared primitive container
    private val data = ArraySlotMap(capacity)

    // Lock for cross-thread synchronization between UI and Render threads
    private val lock = SynchronizedObject()

    companion object {
        // --- Internal Reserved Keys (High range for safety) ---
        private const val INTERNAL_BASE = 0x10000
        internal const val INDEX = INTERNAL_BASE + 0
        internal const val COUNT = INTERNAL_BASE + 1
        internal const val PROGRESS = INTERNAL_BASE + 2
        internal const val TIME = INTERNAL_BASE + 3
        internal const val DELTA_TIME = INTERNAL_BASE + 4

        // --- User-defined Slots (Low range) ---
        const val ORIGIN = 0
        const val RESOLUTION = 1
    }

    // --- Offset Ops (Packed: 2 x Float32 -> Long64) ---
    fun setOffset(key: Int, offset: Offset): ParticleContext = synchronized(lock) {
        val packed = (offset.x.toRawBits().toLong() shl 32) or (offset.y.toRawBits().toLong() and 0xFFFFFFFFL)
        data.setLong(key, packed)
        this
    }

    fun getOffset(key: Int): Offset = synchronized(lock) {
        val raw = data.getLong(key)
        val x = Float.fromBits((raw shr 32).toInt())
        val y = Float.fromBits((raw and 0xFFFFFFFFL).toInt())
        Offset(x, y)
    }

    // --- Size Ops (Packed: 2 x Float32 -> Long64) ---
    fun setSize(key: Int, size: Size): ParticleContext = synchronized(lock) {
        val packed = (size.width.toRawBits().toLong() shl 32) or (size.height.toRawBits().toLong() and 0xFFFFFFFFL)
        data.setLong(key, packed)
        this
    }

    fun getSize(key: Int): Size = synchronized(lock) {
        val raw = data.getLong(key)
        val w = Float.fromBits((raw shr 32).toInt())
        val h = Float.fromBits((raw and 0xFFFFFFFFL).toInt())
        Size(w, h)
    }

    // --- Rect Ops (Occupies 2 consecutive slots) ---
    fun setRect(key: Int, rect: Rect): ParticleContext = synchronized(lock) {
        setOffset(key, Offset(rect.left, rect.top))
        setOffset(key + 1, Offset(rect.right, rect.bottom))
        this
    }

    fun getRect(key: Int): Rect = synchronized(lock) {
        val topLeft = getOffset(key)
        val bottomRight = getOffset(key + 1)
        Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    // --- Double Ops ---
    fun setDouble(key: Int, value: Double): ParticleContext = synchronized(lock) {
        data.setDouble(key, value)
        this
    }
    fun getDouble(key: Int): Double = synchronized(lock) {
        data.getDouble(key)
    }

    // --- Long Ops ---
    fun setLong(key: Int, value: Long): ParticleContext = synchronized(lock) {
        data.setLong(key, value)
        this
    }
    fun getLong(key: Int): Long = synchronized(lock) {
        data.getLong(key)
    }

    // --- Float Ops ---
    fun setFloat(key: Int, value: Float): ParticleContext = synchronized(lock) {
        data.setFloat(key, value)
        this
    }
    fun getFloat(key: Int): Float = synchronized(lock) {
        data.getFloat(key)
    }

    // --- Int Ops ---
    fun setInt(key: Int, value: Int): ParticleContext = synchronized(lock) {
        data.setInt(key, value)
        this
    }
    fun getInt(key: Int): Int = synchronized(lock) {
        data.getInt(key)
    }

    // --- Boolean Ops ---
    fun setBoolean(key: Int, value: Boolean): ParticleContext = synchronized(lock) {
        data.setInt(key, if (value) 1 else 0)
        this
    }
    fun getBoolean(key: Int): Boolean = synchronized(lock) {
        data.getInt(key) == 1
    }
}

enum class SlotStrategy {
    HighFloat,
    LowFloat,
    FullDouble, 
    ColorR, ColorG, ColorB, ColorA,
    ARGB,
    RawInt
}