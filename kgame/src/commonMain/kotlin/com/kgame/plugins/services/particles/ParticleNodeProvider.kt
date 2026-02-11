package com.kgame.plugins.services.particles

import com.kgame.plugins.services.particles.ParticleNode.Scalar
import com.kgame.plugins.services.particles.ParticleNode.Vector2

/**
 * A capability interface that provides shorthand methods to create ParticleNodes.
 * Implemented by scopes to enable a clean, math-like DSL.
 */
interface ParticleNodeProvider {
    // Basic Constants
    fun scalar(value: Float): ParticleNode = Scalar(value)
    fun vec2(x: ParticleNode, y: ParticleNode): ParticleNode = Vector2(x, y)
    fun vec2(x: ParticleNode, y: Float): ParticleNode = Vector2(x, scalar(y))
    fun vec2(x: Float, y: ParticleNode): ParticleNode = Vector2(scalar(x), y)
    fun vec2(x: Float, y: Float): ParticleNode = Vector2(scalar(x), scalar(y))

    // Randomness & Logic
    fun random(min: Float, max: Float, exp: Float = 1.0f): ParticleNode = 
        ParticleNode.RandomRange(min, max, exp)

    fun select(condition: SelectCondition, onTrue: ParticleNode, onFalse: ParticleNode): ParticleNode =
        ParticleNode.Select(condition, onTrue, onFalse)

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

    fun color(red: Float, green: Float, blue: Float, alpha: Float = 1f): ParticleNode =
        color(scalar(red), scalar(green), scalar(blue), scalar(alpha))

    val time: ParticleNode get() = ParticleNode.Time
    val deltaTime: ParticleNode get() = ParticleNode.DeltaTime
    val index: ParticleNode get() = ParticleNode.Index
    val progress: ParticleNode get() = ParticleNode.Progress
    val count: ParticleNode get() = ParticleNode.Count

    val origin: ParticleNode get() = ParticleNode.Origin
    val resolution: ParticleNode get() = ParticleNode.Resolution

    fun Ratio(value: Float) = SelectCondition.Ratio(value)
    fun Threshold(value: Int) = SelectCondition.Threshold(value)
    fun Modulo(value: Int) = SelectCondition.Modulo(value)

    val math: ParticleNodeMath get() = ParticleNodeMath

    operator fun Float.minus(node: ParticleNode): ParticleNode = scalar(this) - node
    operator fun Float.plus(node: ParticleNode): ParticleNode = scalar(this) + node
    operator fun Float.times(node: ParticleNode): ParticleNode = scalar(this) * node
    operator fun Float.div(node: ParticleNode): ParticleNode = scalar(this) / node
}

object ParticleNodeMath {
    val PI: ParticleNode = Scalar(kotlin.math.PI.toFloat())

    // --- Unit Conversions ---
    fun toRadians(degrees: ParticleNode): ParticleNode = (PI / Scalar(180.0f)) * degrees
    fun toRadians(degrees: Float): ParticleNode = (PI / 180.0f) * degrees

    fun toDegrees(radians: ParticleNode): ParticleNode = (Scalar(180.0f) / PI) * radians
    fun toDegrees(radians: Float): ParticleNode = (Scalar(180.0f) / PI) * radians

    // -- Compare --
    fun min(first: ParticleNode, second: ParticleNode): ParticleNode = ParticleNode.Min(first, second)
    fun min(first: ParticleNode, second: Float): ParticleNode = ParticleNode.Min(first, Scalar(second))
    fun min(first: Float, second: ParticleNode): ParticleNode = ParticleNode.Min(Scalar(first), second)
    fun max(first: ParticleNode, second: ParticleNode): ParticleNode = ParticleNode.Max(first, second)
    fun max(first: ParticleNode, second: Float): ParticleNode = ParticleNode.Max(first, Scalar(second))
    fun max(first: Float, second: ParticleNode): ParticleNode = ParticleNode.Max(Scalar(first), second)

    fun clamp(value: ParticleNode, min: ParticleNode, max: ParticleNode): ParticleNode = ParticleNode.Clamp(value, min, max)
    fun clamp(value: ParticleNode, min: Float, max: Float): ParticleNode = ParticleNode.Clamp(value, Scalar(min), Scalar(max))

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

    // --- Power ---
    fun pow(base: ParticleNode, exp: ParticleNode): ParticleNode = ParticleNode.Pow(base, exp)
    fun pow(base: ParticleNode, exp: Float): ParticleNode = ParticleNode.Pow(base, Scalar(exp))
    fun pow(base: Float, exp: ParticleNode): ParticleNode = ParticleNode.Pow(Scalar(base), exp)
    fun pow(base: Float, exp: Float): ParticleNode = ParticleNode.Pow(Scalar(base), Scalar(exp))

    // --- Linear Interpolation (mix) ---
    fun mix(start: ParticleNode, end: ParticleNode, t: ParticleNode): ParticleNode =
        ParticleNode.Mix(start, end, t)
    fun mix(start: Float, end: Float, t: ParticleNode): ParticleNode =
        ParticleNode.Mix(Scalar(start), Scalar(end), t)

    // --- Step Functions ---
    fun step(edge: Float, v: ParticleNode): ParticleNode = ParticleNode.Step(Scalar(edge), v)
    fun smoothstep(edge0: Float, edge1: Float, v: ParticleNode): ParticleNode =
        ParticleNode.SmoothStep(Scalar(edge0), Scalar(edge1), v)

    // --- Shaping & Cycles ---
    // Returns the fractional part of x (x - floor(x)).
    // Powerful for looping: math.fract(time * 2.0) creates a 0..1 ramp every 0.5s.
    fun fract(node: ParticleNode): ParticleNode = ParticleNode.Fract(node)
    fun fract(value: Float): ParticleNode = ParticleNode.Fract(Scalar(value))

    // Modulo: x - y * floor(x/y). Useful for grouping particles by index.
    fun mod(x: ParticleNode, y: ParticleNode): ParticleNode = ParticleNode.Mod(x, y)
    fun mod(x: ParticleNode, y: Float): ParticleNode = ParticleNode.Mod(x, Scalar(y))
    fun mod(x: Float, y: ParticleNode): ParticleNode = ParticleNode.Mod(Scalar(x), y)

    // --- Rounding & Sign ---
    fun floor(node: ParticleNode): ParticleNode = ParticleNode.Floor(node)
    fun floor(value: Float): ParticleNode = ParticleNode.Floor(Scalar(value))

    fun ceil(node: ParticleNode): ParticleNode = ParticleNode.Ceil(node)
    fun ceil(value: Float): ParticleNode = ParticleNode.Ceil(Scalar(value))

    // Returns -1.0 for negative, 0.0 for zero, and 1.0 for positive.
    fun sign(node: ParticleNode): ParticleNode = ParticleNode.Sign(node)
    fun sign(value: Float): ParticleNode = ParticleNode.Sign(Scalar(value))
}