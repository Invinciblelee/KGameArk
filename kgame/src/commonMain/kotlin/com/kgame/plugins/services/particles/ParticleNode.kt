package com.kgame.plugins.services.particles

import kotlin.jvm.JvmInline

/**
 * Unified node tree for particle logic.
 * These nodes are the "Single Source of Truth" for both CPU and GPU.
 */
sealed class ParticleNode {
    data class Scalar(val value: Float) : ParticleNode()
    data class Vector2(val x: ParticleNode, val y: ParticleNode) : ParticleNode()
    data class Multiply(val left: ParticleNode, val right: ParticleNode) : ParticleNode()
    data class Add(val left: ParticleNode, val right: ParticleNode) : ParticleNode()
    data class Subtract(val left: ParticleNode, val right: ParticleNode) : ParticleNode()
    data class Divide(val left: ParticleNode, val right: ParticleNode) : ParticleNode()
    data class Sin(val node: ParticleNode) : ParticleNode()
    data class Cos(val node: ParticleNode) : ParticleNode()
    data class Pow(val base: ParticleNode, val exponent: ParticleNode) : ParticleNode()
    data class RandomRange(val min: Float, val max: Float, val exp: Float = 1.0f) : ParticleNode()
    data class Select(
        val condition: SelectCondition,
        val onTrue: ParticleNode,
        val onFalse: ParticleNode
    ) : ParticleNode()

    data class Color(val argb: Int) : ParticleNode()

    object Time : ParticleNode()
    object Index : ParticleNode()
    object Count : ParticleNode()
    object Progress : ParticleNode()

    operator fun plus(other: ParticleNode) = Add(this, other)
    operator fun minus(other: ParticleNode) = Subtract(this, other)
    operator fun times(other: ParticleNode) = Multiply(this, other)
    operator fun div(other: ParticleNode) = Divide(this, other)

    operator fun plus(other: Float) = Add(this, Scalar(other))
    operator fun minus(other: Float) = Subtract(this, Scalar(other))
    operator fun times(other: Float) = Multiply(this, Scalar(other))
    operator fun div(other: Float) = Divide(this, Scalar(other))

    fun sin(node: ParticleNode) = Sin(node)
    fun cos(node: ParticleNode) = Cos(node)
    fun pow(base: ParticleNode, exponent: ParticleNode) = Pow(base, exponent)
    fun pow(base: ParticleNode, exponent: Float) = Pow(base, ParticleNode.Scalar(exponent))
}

sealed interface SelectCondition {
    @JvmInline
    value class Ratio(val value: Float) : SelectCondition
    @JvmInline
    value class Threshold(val value: Int) : SelectCondition
    @JvmInline
    value class Modulo(val divisor: Int) : SelectCondition
}