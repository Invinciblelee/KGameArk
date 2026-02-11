package com.kgame.plugins.services.particles

import androidx.compose.ui.util.packFloats
import androidx.compose.ui.util.unpackFloat1
import androidx.compose.ui.util.unpackFloat2
import com.kgame.engine.geometry.Vector2
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

    companion object Companion {
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

        val Default: ParticleContext = ParticleContext()
    }

    fun setVector2(key: Int, vec2: Vector2): ParticleContext = synchronized(lock) {
        data.setLong(key, packFloats(vec2.x, vec2.y))
        this
    }

    fun getVector2(key: Int): Vector2 = synchronized(lock) {
        val raw = data.getLong(key)
        Vector2(raw)
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

    fun getFloat(key: Int, mapping: AttributeMapping): Float {
        val value = getLong(key)
        return when (mapping) {
            AttributeMapping.HighFloat -> unpackFloat2(value)
            AttributeMapping.LowFloat  -> unpackFloat1(value)
            AttributeMapping.Double -> Double.fromBits(value).toFloat()
            else -> error("SlotStrategy $mapping cannot be resolved as Float")
        }
    }

    fun getInt(key: Int, mapping: AttributeMapping): Int {
        val value = getLong(key)
        return when (mapping) {
            AttributeMapping.Int -> (value and 0xFFFFFFFFL).toInt()
            else -> error("SlotStrategy $mapping cannot be resolved as Int")
        }
    }
}

enum class AttributeMapping {
    HighFloat,
    LowFloat,
    Double,
    Int
}