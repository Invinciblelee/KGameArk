package com.kgame.plugins.services.particles

import kotlin.jvm.JvmStatic
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * High-performance Lookup Table (LUT) for Particle SDK.
 * Optimized for mobile CPU architectures with 0-GC and bitwise operations.
 */
internal object ParticleLut {

    // --- Sin/Cos Configuration (2048 entries) ---
    private const val SIN_BITS = 11
    private const val SIN_SIZE = 1 shl SIN_BITS
    private const val SIN_MASK = SIN_SIZE - 1
    private const val TWO_PI = (PI * 2.0).toFloat()
    private const val RAD_TO_INDEX = SIN_SIZE / TWO_PI
    private const val COS_OFFSET = SIN_SIZE / 4

    // --- Exp Configuration (1024 entries for range [0, 5.0]) ---
    private const val EXP_BITS = 10
    private const val EXP_SIZE = 1 shl EXP_BITS
    private const val EXP_MASK = EXP_SIZE - 1
    // We map 0.0 to 5.0 (beyond 5.0, exp(-x) is nearly 0)
    private const val EXP_MAX_INPUT = 5.0f
    private const val EXP_TO_INDEX = EXP_SIZE / EXP_MAX_INPUT

    // --- Static Buffers ---
    private val SIN_TABLE = FloatArray(SIN_SIZE)
    private val EXP_TABLE = FloatArray(EXP_SIZE)

    init {
        // Pre-compute Sine table
        for (i in 0 until SIN_SIZE) {
            SIN_TABLE[i] = sin(i * (TWO_PI / SIN_SIZE))
        }

        // Pre-compute Exp table for e^(-x) where x is [0, 5]
        for (i in 0 until EXP_SIZE) {
            EXP_TABLE[i] = exp(-(i / EXP_TO_INDEX))
        }
    }

    /**
     * Fast Sine approximation.
     */
    @JvmStatic
    fun fastSin(radians: Float): Float {
        val idx = (radians * RAD_TO_INDEX).toInt() and SIN_MASK
        return SIN_TABLE[idx]
    }

    /**
     * Fast Cosine approximation.
     */
    @JvmStatic
    fun fastCos(radians: Float): Float {
        val idx = ((radians * RAD_TO_INDEX).toInt() + COS_OFFSET) and SIN_MASK
        return SIN_TABLE[idx]
    }

    /**
     * Fast Exponential approximation for e^v.
     * Special optimization for negative inputs (common in drag/fade logic).
     */
    @JvmStatic
    fun fastExp(v: Float): Float {
        // If v is in range [-5.0, 0.0], use LUT
        if (v <= 0f && v > -EXP_MAX_INPUT) {
            val idx = (-v * EXP_TO_INDEX).toInt() and EXP_MASK
            return EXP_TABLE[idx]
        }
        // Fallback for positive or extreme negative values
        return exp(v)
    }
}