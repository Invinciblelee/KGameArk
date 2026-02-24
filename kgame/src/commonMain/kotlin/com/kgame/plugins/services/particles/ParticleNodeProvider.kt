package com.kgame.plugins.services.particles

import com.kgame.plugins.services.particles.ParticleNode.Add
import com.kgame.plugins.services.particles.ParticleNode.Combine
import com.kgame.plugins.services.particles.ParticleNode.Comparison
import com.kgame.plugins.services.particles.ParticleNode.Component
import com.kgame.plugins.services.particles.ParticleNode.Divide
import com.kgame.plugins.services.particles.ParticleNode.Mod
import com.kgame.plugins.services.particles.ParticleNode.Multiply
import com.kgame.plugins.services.particles.ParticleNode.Not
import com.kgame.plugins.services.particles.ParticleNode.Sample
import com.kgame.plugins.services.particles.ParticleNode.Scalar
import com.kgame.plugins.services.particles.ParticleNode.Subtract
import com.kgame.plugins.services.particles.ParticleNode.Vector2

/**
 * A capability interface that provides shorthand methods to create ParticleNodes.
 * Implemented by scopes to enable a clean, math-like DSL.
 */
@ParticleDslMarker
interface ParticleNodeProvider {
    // Basic Constants
    fun scalar(value: Float): ParticleNode = Scalar(value)
    fun vec2(x: ParticleNode, y: ParticleNode): ParticleNode = Vector2(x, y)
    fun vec2(x: ParticleNode, y: Float): ParticleNode = Vector2(x, scalar(y))
    fun vec2(x: Float, y: ParticleNode): ParticleNode = Vector2(scalar(x), y)
    fun vec2(x: Float, y: Float): ParticleNode = Vector2(scalar(x), scalar(y))

    fun color(argb: Int): ParticleNode {
        return ParticleNode.Color(
            red = scalar(((argb ushr 16) and 0xFF) / 255f),
            green = scalar(((argb ushr 8) and 0xFF) / 255f),
            blue = scalar((argb and 0xFF) / 255f),
            alpha = scalar(((argb ushr 24) and 0xFF) / 255f)
        )
    }

    fun color(argb: Long): ParticleNode = color(argb.toInt())

    fun color(
        red: ParticleNode,
        green: ParticleNode,
        blue: ParticleNode,
        alpha: ParticleNode = scalar(1f)
    ): ParticleNode = ParticleNode.Color(red, green, blue, alpha)

    fun color(
        red: ParticleNode,
        green: ParticleNode,
        blue: ParticleNode,
        alpha: Float
    ): ParticleNode = ParticleNode.Color(red, green, blue, scalar(alpha))

    fun color(red: Float, green: Float, blue: Float, alpha: ParticleNode): ParticleNode =
        color(scalar(red), scalar(green), scalar(blue), alpha)

    fun color(red: Float, green: Float, blue: Float, alpha: Float = 1f): ParticleNode =
        color(scalar(red), scalar(green), scalar(blue), scalar(alpha))

    val math: ParticleNodeMath
    val env: ParticleNodeEnvironment
    val ops: ParticleNodeOperations

    operator fun ParticleNode.plus(other: ParticleNode): ParticleNode = Add(this, other)
    operator fun ParticleNode.minus(other: ParticleNode): ParticleNode = Subtract(this, other)
    operator fun ParticleNode.times(other: ParticleNode): ParticleNode = Multiply(this, other)
    operator fun ParticleNode.div(other: ParticleNode): ParticleNode = Divide(this, other)
    operator fun ParticleNode.unaryMinus(): ParticleNode = Multiply(Scalar(-1f), this)
    operator fun ParticleNode.rem(other: Float): ParticleNode = Mod(this, Scalar(other))
    operator fun ParticleNode.plus(other: Float): ParticleNode = Add(this, Scalar(other))
    operator fun ParticleNode.minus(other: Float): ParticleNode = Subtract(this, Scalar(other))
    operator fun ParticleNode.times(other: Float): ParticleNode = Multiply(this, Scalar(other))
    operator fun ParticleNode.div(other: Float): ParticleNode = Divide(this, Scalar(other))

    operator fun Float.minus(node: ParticleNode): ParticleNode = scalar(this) - node
    operator fun Float.plus(node: ParticleNode): ParticleNode = scalar(this) + node
    operator fun Float.times(node: ParticleNode): ParticleNode = scalar(this) * node
    operator fun Float.div(node: ParticleNode): ParticleNode = scalar(this) / node

    operator fun ParticleNode.not(): ParticleNode = Not(this)
    operator fun ParticleNode.get(index: Int): ParticleNode = Component(this, index)
    operator fun ParticleNode.invoke(t: ParticleNode): ParticleNode = Sample(this, t)
    operator fun ParticleNode.invoke(p: Float): ParticleNode = Sample(this, Scalar(p))

    infix fun ParticleNode.gt(other: ParticleNode): ParticleNode =
        Comparison(this, other, ComparisonOp.GreaterThan)

    infix fun ParticleNode.lt(other: ParticleNode): ParticleNode =
        Comparison(this, other, ComparisonOp.LessThan)

    infix fun ParticleNode.ge(other: ParticleNode): ParticleNode =
        Comparison(this, other, ComparisonOp.GreaterEqual)

    infix fun ParticleNode.le(other: ParticleNode): ParticleNode =
        Comparison(this, other, ComparisonOp.LessEqual)

    infix fun ParticleNode.eq(other: ParticleNode): ParticleNode =
        Comparison(this, other, ComparisonOp.Equal)

    infix fun ParticleNode.ne(other: ParticleNode): ParticleNode =
        Comparison(this, other, ComparisonOp.NotEqual)

    infix fun ParticleNode.gt(other: Float): ParticleNode = gt(Scalar(other))
    infix fun ParticleNode.lt(other: Float): ParticleNode = lt(Scalar(other))
    infix fun ParticleNode.ge(other: Float): ParticleNode = ge(Scalar(other))
    infix fun ParticleNode.le(other: Float): ParticleNode = le(Scalar(other))
    infix fun ParticleNode.eq(other: Float): ParticleNode = eq(Scalar(other))
    infix fun ParticleNode.ne(other: Float): ParticleNode = ne(Scalar(other))

    infix fun ParticleNode.and(other: ParticleNode): ParticleNode =
        Combine(this, other, CombineOp.And)

    infix fun ParticleNode.or(other: ParticleNode): ParticleNode =
        Combine(this, other, CombineOp.Or)
}

class ParticleNodeEnvironment {
    val time: ParticleNode get() = ParticleNode.Time
    val deltaTime: ParticleNode get() = ParticleNode.DeltaTime
    val index: ParticleNode get() = ParticleNode.Index
    val progress: ParticleNode get() = ParticleNode.Progress
    val count: ParticleNode get() = ParticleNode.Count
    val origin: ParticleNode get() = ParticleNode.Origin
}

/**
 * Graphical Operations & Composite Logic.
 * Optimized with multiple overloads to minimize explicit Scalar wrapping.
 */
class ParticleNodeOperations {

    // --- Linear Interpolation (mix) ---

    fun mix(from: ParticleNode, to: ParticleNode, factor: ParticleNode) =
        ParticleNode.Mix(from, to, factor)

    fun mix(from: ParticleNode, to: Float, factor: ParticleNode) =
        ParticleNode.Mix(from, Scalar(to), factor)

    fun mix(from: Float, to: ParticleNode, factor: ParticleNode) =
        ParticleNode.Mix(Scalar(from), to, factor)

    fun mix(from: Float, to: Float, factor: ParticleNode) =
        ParticleNode.Mix(Scalar(from), Scalar(to), factor)

    // --- Constraints (clamp) ---

    fun clamp(value: ParticleNode, min: ParticleNode, max: ParticleNode) =
        ParticleNode.Clamp(value, min, max)

    fun clamp(value: ParticleNode, min: Float, max: Float) =
        ParticleNode.Clamp(value, Scalar(min), Scalar(max))

    fun clamp(value: ParticleNode, min: ParticleNode, max: Float) =
        ParticleNode.Clamp(value, min, Scalar(max))

    fun clamp(value: ParticleNode, min: Float, max: ParticleNode) =
        ParticleNode.Clamp(value, Scalar(min), max)

    // --- Thresholding (step) ---

    fun step(threshold: ParticleNode, input: ParticleNode) =
        ParticleNode.Step(threshold, input)

    fun step(threshold: Float, input: ParticleNode) =
        ParticleNode.Step(Scalar(threshold), input)

    // --- Smooth Interpolation (smoothstep) ---

    fun smoothstep(from: ParticleNode, to: ParticleNode, input: ParticleNode) =
        ParticleNode.SmoothStep(from, to, input)

    fun smoothstep(from: Float, to: Float, input: ParticleNode) =
        ParticleNode.SmoothStep(Scalar(from), Scalar(to), input)

    fun smoothstep(from: Float, to: ParticleNode, input: ParticleNode) =
        ParticleNode.SmoothStep(Scalar(from), to, input)

    fun smoothstep(from: ParticleNode, to: Float, input: ParticleNode) =
        ParticleNode.SmoothStep(from, Scalar(to), input)

    // --- Logical Selection (select) ---

    fun select(condition: ParticleNode, onTrue: ParticleNode, onFalse: ParticleNode) =
        ParticleNode.Select(condition, onTrue, onFalse)

    fun select(condition: ParticleNode, onTrue: Float, onFalse: Float) =
        ParticleNode.Select(condition, Scalar(onTrue), Scalar(onFalse))

    fun select(condition: ParticleNode, onTrue: ParticleNode, onFalse: Float) =
        ParticleNode.Select(condition, onTrue, Scalar(onFalse))

    fun select(condition: ParticleNode, onTrue: Float, onFalse: ParticleNode) =
        ParticleNode.Select(condition, Scalar(onTrue), onFalse)

    // --- Procedural ---

    fun noise(input: ParticleNode, min: Float = 0f, max: Float = 1f, octaves: Int = 1) =
        ParticleNode.Noise(input, min, max, octaves)
}

/**
 * Mathematical Operations.
 */
class ParticleNodeMath(val provider: ParticleNodeProvider) {

    companion object {
        val PI: ParticleNode = Scalar(kotlin.math.PI.toFloat())
        val TAU: ParticleNode = Scalar(kotlin.math.PI.toFloat() * 2f)
        val E: ParticleNode = Scalar(kotlin.math.E.toFloat())

        /** * Constant to convert degrees to radians.
         * Calculation: PI / 180.0
         */
        val D2R: ParticleNode = Scalar(0.017453292f)

        /** * Constant to convert radians to degrees.
         * Calculation: 180.0 / PI
         */
        val R2D: ParticleNode = Scalar(57.29578f)
    }

    // --- Unit Conversions ---
    fun toRadians(degrees: ParticleNode): ParticleNode = with(provider) { D2R * degrees }
    fun toRadians(degrees: Float): ParticleNode = with(provider) { D2R * degrees }

    fun toDegrees(radians: ParticleNode): ParticleNode = with(provider) { R2D * radians }
    fun toDegrees(radians: Float): ParticleNode = with(provider) { R2D * radians }

    // --- Vector Math Operators ---
    fun dot(left: ParticleNode, right: ParticleNode): ParticleNode = ParticleNode.Dot(left, right)
    fun dot(left: ParticleNode, right: Float): ParticleNode = ParticleNode.Dot(left, Scalar(right))

    fun length(node: ParticleNode): ParticleNode = ParticleNode.Length(node)
    fun length(value: Float): ParticleNode = ParticleNode.Length(Scalar(value))

    fun distance(p1: ParticleNode, p2: ParticleNode): ParticleNode = ParticleNode.Distance(p1, p2)
    fun distance(p1: ParticleNode, p2: Float): ParticleNode = ParticleNode.Distance(p1, Scalar(p2))
    fun distance(p1: Float, p2: ParticleNode): ParticleNode = ParticleNode.Distance(Scalar(p1), p2)

    // -- Compare --
    fun min(first: ParticleNode, second: ParticleNode): ParticleNode =
        ParticleNode.Min(first, second)

    fun min(first: ParticleNode, second: Float): ParticleNode =
        ParticleNode.Min(first, Scalar(second))

    fun min(first: Float, second: ParticleNode): ParticleNode =
        ParticleNode.Min(Scalar(first), second)

    fun max(first: ParticleNode, second: ParticleNode): ParticleNode =
        ParticleNode.Max(first, second)

    fun max(first: ParticleNode, second: Float): ParticleNode =
        ParticleNode.Max(first, Scalar(second))

    fun max(first: Float, second: ParticleNode): ParticleNode =
        ParticleNode.Max(Scalar(first), second)

    // --- Basic Functions ---
    fun abs(node: ParticleNode): ParticleNode = ParticleNode.Abs(node)
    fun abs(value: Float): ParticleNode = ParticleNode.Abs(Scalar(value))

    fun sqrt(node: ParticleNode): ParticleNode = ParticleNode.Sqrt(node)
    fun sqrt(value: Float): ParticleNode = ParticleNode.Sqrt(Scalar(value))

    fun exp(node: ParticleNode): ParticleNode = ParticleNode.Exp(node) // e^x
    fun exp(value: Float): ParticleNode = ParticleNode.Exp(Scalar(value))

    // --- Sine ---
    fun sin(node: ParticleNode): ParticleNode = ParticleNode.Sin(node)
    fun sin(value: Float): ParticleNode = ParticleNode.Sin(Scalar(value))

    // --- Cosine ---
    fun cos(node: ParticleNode): ParticleNode = ParticleNode.Cos(node)
    fun cos(value: Float): ParticleNode = ParticleNode.Cos(Scalar(value))

    // --- Trig ---
    fun tan(node: ParticleNode): ParticleNode = ParticleNode.Tan(node)
    fun tan(value: Float): ParticleNode = ParticleNode.Tan(Scalar(value))

    // --- Atan ---
    fun atan(node: ParticleNode): ParticleNode = ParticleNode.Atan(node)
    fun atan(value: Float): ParticleNode = ParticleNode.Atan(Scalar(value))
    fun atan2(y: ParticleNode, x: ParticleNode): ParticleNode = ParticleNode.Atan2(y, x)
    fun atan2(y: ParticleNode, x: Float): ParticleNode = ParticleNode.Atan2(y, Scalar(x))
    fun atan2(y: Float, x: ParticleNode): ParticleNode = ParticleNode.Atan2(Scalar(y), x)
    fun atan2(y: Float, x: Float): ParticleNode = ParticleNode.Atan2(Scalar(y), Scalar(x))

    // --- Power ---
    fun pow(base: ParticleNode, exp: ParticleNode): ParticleNode = ParticleNode.Pow(base, exp)
    fun pow(base: ParticleNode, exp: Float): ParticleNode = ParticleNode.Pow(base, Scalar(exp))

    // --- Shaping & Cycles ---
    // Returns the fractional part of x (x - floor(x)).
    // Powerful for looping: math.fract(time * 2.0) creates a 0..1 ramp every 0.5s.
    fun fract(node: ParticleNode): ParticleNode = ParticleNode.Fract(node)
    fun fract(value: Float): ParticleNode = ParticleNode.Fract(Scalar(value))

    // Modulo: x - y * floor(x/y). Useful for grouping particles by index.
    fun mod(x: ParticleNode, y: ParticleNode): ParticleNode = ParticleNode.Mod(x, y)
    fun mod(x: ParticleNode, y: Float): ParticleNode = ParticleNode.Mod(x, Scalar(y))

    // --- Rounding & Sign ---
    fun floor(node: ParticleNode): ParticleNode = ParticleNode.Floor(node)
    fun floor(value: Float): ParticleNode = ParticleNode.Floor(Scalar(value))

    fun ceil(node: ParticleNode): ParticleNode = ParticleNode.Ceil(node)
    fun ceil(value: Float): ParticleNode = ParticleNode.Ceil(Scalar(value))

    // Returns -1.0 for negative, 0.0 for zero, and 1.0 for positive.
    fun sign(node: ParticleNode): ParticleNode = ParticleNode.Sign(node)
    fun sign(value: Float): ParticleNode = ParticleNode.Sign(Scalar(value))

    // Random
    fun random(min: ParticleNode, max: ParticleNode, seed: ParticleNode? = null) =
        ParticleNode.Random(min, max, seed)
    fun random(min: Float, max: Float, seed: ParticleNode? = null) =
        ParticleNode.Random(Scalar(min), Scalar(max), seed)
}