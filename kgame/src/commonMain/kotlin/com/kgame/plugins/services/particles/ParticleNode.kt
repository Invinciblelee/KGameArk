package com.kgame.plugins.services.particles

import kotlin.jvm.JvmInline

/**
 * Unified node tree for particle logic.
 * These nodes are the "Single Source of Truth" for both CPU and GPU.
 */
sealed interface ParticleNode {
    @JvmInline
    value class Scalar(val value: Float) : ParticleNode
    open class Vector2(val x: ParticleNode, val y: ParticleNode) : ParticleNode

    data class Multiply(val left: ParticleNode, val right: ParticleNode) : ParticleNode
    data class Add(val left: ParticleNode, val right: ParticleNode) : ParticleNode
    data class Subtract(val left: ParticleNode, val right: ParticleNode) : ParticleNode
    data class Divide(val left: ParticleNode, val right: ParticleNode) : ParticleNode

    data class Dot(val left: ParticleNode, val right: ParticleNode) : ParticleNode
    data class Length(val node: ParticleNode) : ParticleNode
    data class Distance(val p1: ParticleNode, val p2: ParticleNode) : ParticleNode

    data class Sin(val node: ParticleNode) : ParticleNode
    data class Cos(val node: ParticleNode) : ParticleNode
    data class Tan(val node: ParticleNode) : ParticleNode
    data class Atan(val node: ParticleNode) : ParticleNode
    data class Atan2(val y: ParticleNode, val x: ParticleNode) : ParticleNode

    data class Abs(val node: ParticleNode) : ParticleNode
    data class Sqrt(val node: ParticleNode) : ParticleNode
    data class Exp(val node: ParticleNode) : ParticleNode
    data class Pow(val base: ParticleNode, val exponent: ParticleNode) : ParticleNode

    data class Mix(val from: ParticleNode, val to: ParticleNode, val factor: ParticleNode) :
        ParticleNode

    data class Step(val threshold: ParticleNode, val input: ParticleNode) : ParticleNode
    data class LinearStep(val from: ParticleNode, val to: ParticleNode, val input: ParticleNode) :
        ParticleNode

    data class SmoothStep(val from: ParticleNode, val to: ParticleNode, val input: ParticleNode) :
        ParticleNode

    data class Noise(
        val input: ParticleNode,
        val min: Float = 0f,
        val max: Float = 1f,
        val octaves: Int = 1
    ) : ParticleNode

    data class Max(val first: ParticleNode, val second: ParticleNode) : ParticleNode
    data class Min(val first: ParticleNode, val second: ParticleNode) : ParticleNode
    data class Clamp(val value: ParticleNode, val min: ParticleNode, val max: ParticleNode) :
        ParticleNode

    data class Fract(val node: ParticleNode) : ParticleNode
    data class Mod(val left: ParticleNode, val right: ParticleNode) : ParticleNode
    data class Floor(val node: ParticleNode) : ParticleNode
    data class Ceil(val node: ParticleNode) : ParticleNode
    data class Sign(val node: ParticleNode) : ParticleNode

    data class Not(val node: ParticleNode) : ParticleNode
    data class Component(val node: ParticleNode, val index: Int) : ParticleNode
    data class Sample(val function: ParticleNode, val parameter: ParticleNode) : ParticleNode
    data class Hash(val node: ParticleNode) : ParticleNode

    data class Random(
        val min: ParticleNode,
        val max: ParticleNode,
        val seed: ParticleNode? = null
    ) : ParticleNode

    data class Comparison(
        val left: ParticleNode,
        val right: ParticleNode,
        val op: ComparisonOp
    ) : ParticleNode

    data class Combine(
        val left: ParticleNode,
        val right: ParticleNode,
        val op: CombineOp
    ) : ParticleNode

    data class Select(
        val condition: ParticleNode,
        val onTrue: ParticleNode,
        val onFalse: ParticleNode
    ) : ParticleNode

    data class Color(
        val red: ParticleNode,
        val green: ParticleNode,
        val blue: ParticleNode,
        val alpha: ParticleNode
    ) : ParticleNode

    data object Time : ParticleNode
    data object DeltaTime : ParticleNode
    data object Index : ParticleNode
    data object Count : ParticleNode
    data object Progress : ParticleNode
    data object Origin : ParticleNode

    val hash: ParticleNode get() = Hash(this)
}

enum class ComparisonOp {
    Equal,
    NotEqual,
    GreaterThan,
    GreaterEqual,
    LessThan,
    LessEqual
}

enum class CombineOp {
    And,
    Or
}