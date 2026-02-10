package com.kgame.plugins.services.particles

import kotlin.jvm.JvmInline

/**
 * Unified node tree for particle logic.
 * These nodes are the "Single Source of Truth" for both CPU and GPU.
 */
sealed class ParticleNode {
    data class Scalar(val value: Float) : ParticleNode()
    data class Vector2(val x: Float, val y: Float) : ParticleNode()
    data class Multiply(val left: ParticleNode, val right: ParticleNode) : ParticleNode()
    data class Add(val left: ParticleNode, val right: ParticleNode) : ParticleNode()
    data class RandomRange(val min: Float, val max: Float, val exp: Float = 1.0f) : ParticleNode()
    data class Select(
        val condition: SelectCondition,
        val onTrue: ParticleNode,
        val onFalse: ParticleNode
    ) : ParticleNode()

    data class Color(val argb: Int) : ParticleNode()

    object Time : ParticleNode()
    object Index : ParticleNode()
    object Progress : ParticleNode()

    operator fun plus(other: ParticleNode) = Add(this, other)
    operator fun times(other: ParticleNode) = Multiply(this, other)
    operator fun minus(other: ParticleNode) = Add(this, Multiply(other, Scalar(-1f)))
}

sealed interface SelectCondition {
    @JvmInline
    value class Ratio(val value: Float) : SelectCondition
    @JvmInline
    value class Threshold(val value: Int) : SelectCondition
    @JvmInline
    value class Modulo(val divisor: Int) : SelectCondition
}