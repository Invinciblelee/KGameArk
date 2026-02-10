package com.kgame.plugins.services.particles

/**
 * Single-array storage for both Int and Float.
 * No extra capacity, no boxing, just 32-bit bit-casting.
 */
class ParticleArgs(capacity: Int = 8) {
    private val data = FloatArray(capacity)

    companion object {
        const val INDEX = 0
        const val COUNT = 1
        const val PROGRESS = 2
        const val TIME = 3
    }

    // --- Float Ops ---
    fun setFloat(key: Int, value: Float): ParticleArgs {
        data[key] = value
        return this
    }
    fun getFloat(key: Int): Float = data[key]

    // --- Int Ops (Using bit conversion) ---
    fun setInt(key: Int, value: Int): ParticleArgs {
        data[key] = Float.fromBits(value) // Bits remain the same
        return this
    }
    fun getInt(key: Int): Int = data[key].toRawBits()
}