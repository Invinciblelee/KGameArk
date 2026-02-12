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
    data class Normalize(val node: ParticleNode) : ParticleNode
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

    data class Mix(val start: ParticleNode, val end: ParticleNode, val t: ParticleNode) : ParticleNode
    data class Step(val edge: ParticleNode, val v: ParticleNode) : ParticleNode
    data class SmoothStep(val e0: ParticleNode, val e1: ParticleNode, val v: ParticleNode) : ParticleNode
    
    data class Max(val first: ParticleNode, val second: ParticleNode) : ParticleNode
    data class Min(val first: ParticleNode, val second: ParticleNode) : ParticleNode
    data class Clamp(val value: ParticleNode, val min: ParticleNode, val max: ParticleNode) : ParticleNode

    data class Fract(val node: ParticleNode): ParticleNode
    data class Mod(val left: ParticleNode, val right: ParticleNode): ParticleNode
    data class Floor(val node: ParticleNode): ParticleNode
    data class Ceil(val node: ParticleNode): ParticleNode
    data class Sign(val node: ParticleNode): ParticleNode

    data class RandomRange(val min: Float, val max: Float, val exp: Float = 1.0f) : ParticleNode

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
        val condition: SelectCondition,
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

    operator fun plus(other: ParticleNode): ParticleNode = Add(this, other)
    operator fun minus(other: ParticleNode): ParticleNode = Subtract(this, other)
    operator fun times(other: ParticleNode): ParticleNode = Multiply(this, other)
    operator fun div(other: ParticleNode): ParticleNode = Divide(this, other)
    operator fun unaryMinus(): ParticleNode = Multiply(Scalar(-1f), this)

    operator fun plus(other: Float): ParticleNode = Add(this, Scalar(other))
    operator fun minus(other: Float): ParticleNode = Subtract(this, Scalar(other))
    operator fun times(other: Float): ParticleNode = Multiply(this, Scalar(other))
    operator fun div(other: Float): ParticleNode = Divide(this, Scalar(other))

    infix fun gt(other: ParticleNode): ParticleNode = Comparison(this, other, ComparisonOp.GreaterThan)
    infix fun lt(other: ParticleNode): ParticleNode = Comparison(this, other, ComparisonOp.LessThan)
    infix fun ge(other: ParticleNode): ParticleNode = Comparison(this, other, ComparisonOp.GreaterEqual)
    infix fun le(other: ParticleNode): ParticleNode = Comparison(this, other, ComparisonOp.LessEqual)
    infix fun eq(other: ParticleNode): ParticleNode = Comparison(this, other, ComparisonOp.Equal)
    infix fun ne(other: ParticleNode): ParticleNode = Comparison(this, other, ComparisonOp.NotEqual)

    infix fun gt(other: Float): ParticleNode = gt(Scalar(other))
    infix fun lt(other: Float): ParticleNode = lt(Scalar(other))
    infix fun ge(other: Float): ParticleNode = ge(Scalar(other))
    infix fun le(other: Float): ParticleNode = le(Scalar(other))
    infix fun eq(other: Float): ParticleNode = eq(Scalar(other))
    infix fun ne(other: Float): ParticleNode = ne(Scalar(other))

    infix fun and(other: ParticleNode): ParticleNode = Combine(this, other, CombineOp.And)
    infix fun or(other: ParticleNode): ParticleNode = Combine(this, other, CombineOp.Or)
}

sealed interface SelectCondition {
    @JvmInline
    value class Ratio(val value: Float) : SelectCondition
    @JvmInline
    value class Threshold(val value: Int) : SelectCondition
    @JvmInline
    value class Modulo(val divisor: Int) : SelectCondition
}

enum class ComparisonOp(val symbol: String) {
    Equal("=="),
    NotEqual("!="),
    GreaterThan(">"),
    GreaterEqual(">="),
    LessThan("<"),
    LessEqual("<=")
}

enum class CombineOp(val symbol: String) {
    And("&&"),
    Or("||")
}