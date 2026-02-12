package com.kgame.plugins.services.particles

import com.kgame.engine.geometry.Vector2

/**
 * Functional interface for evaluating a scalar value (Float).
 * compiled to a specific lambda to minimize virtual call overhead.
 */
fun interface ScalarExec {
    fun eval(ctx: ParticleContext): Float
}

/**
 * Functional interface for evaluating a 2D vector.
 * Returns a [Vector2] value class, ensuring zero object allocation.
 */
fun interface VectorExec {
    fun eval(ctx: ParticleContext): Vector2
}

/**
 * Functional interface for evaluating a color.
 * Returns a packed 32-bit ARGB Integer.
 */
fun interface ColorExec {
    fun eval(ctx: ParticleContext): Int
}

/**
 * Functional interface for boolean condition checks.
 */
fun interface ConditionExec {
    fun check(ctx: ParticleContext): Boolean
}