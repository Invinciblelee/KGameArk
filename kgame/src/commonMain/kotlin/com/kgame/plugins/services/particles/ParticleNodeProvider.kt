package com.kgame.plugins.services.particles

import com.kgame.plugins.services.particles.ParticleNode.Clamp
import com.kgame.plugins.services.particles.ParticleNode.LinearStep
import com.kgame.plugins.services.particles.ParticleNode.Mix
import com.kgame.plugins.services.particles.ParticleNode.Noise
import com.kgame.plugins.services.particles.ParticleNode.Scalar
import com.kgame.plugins.services.particles.ParticleNode.Select
import com.kgame.plugins.services.particles.ParticleNode.SmoothStep
import com.kgame.plugins.services.particles.ParticleNode.Step
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

    val math: ParticleNodeMath get() = ParticleNodeMath
    val env: ParticleNodeEnvironment get() = ParticleNodeEnvironment
    val via: ParticleNodeVia get() = ParticleNodeVia

    operator fun Float.minus(node: ParticleNode): ParticleNode = scalar(this) - node
    operator fun Float.plus(node: ParticleNode): ParticleNode = scalar(this) + node
    operator fun Float.times(node: ParticleNode): ParticleNode = scalar(this) * node
    operator fun Float.div(node: ParticleNode): ParticleNode = scalar(this) / node
}

object ParticleNodeEnvironment {
    val time: ParticleNode get() = ParticleNode.Time
    val deltaTime: ParticleNode get() = ParticleNode.DeltaTime
    val index: ParticleNode get() = ParticleNode.Index
    val progress: ParticleNode get() = ParticleNode.Progress
    val count: ParticleNode get() = ParticleNode.Count
    val origin: ParticleNode get() = ParticleNode.Origin
}

object ParticleNodeVia {

    fun select(condition: ParticleNode, onTrue: ParticleNode, onFalse: ParticleNode) =
        ParticleNode.Select(condition, onTrue, onFalse)

    fun select(condition: ParticleNode, onTrue: Float, onFalse: Float) =
        ParticleNode.Select(condition, Scalar(onTrue), Scalar(onFalse))

    // --- 区间映射与混合 ---
    fun mix(from: ParticleNode, to: ParticleNode, factor: ParticleNode) =
        ParticleNode.Mix(from, to, factor)

    fun mix(from: Float, to: Float, factor: ParticleNode) =
        ParticleNode.Mix(Scalar(from), Scalar(to), factor)

    fun clamp(value: ParticleNode, min: Float, max: Float) =
        ParticleNode.Clamp(value, Scalar(min), Scalar(max))

    // --- 步进整形 ---
    fun step(threshold: ParticleNode, input: ParticleNode) =
        ParticleNode.Step(threshold, input)

    fun smoothstep(from: ParticleNode, to: ParticleNode, input: ParticleNode) =
        ParticleNode.SmoothStep(from, to, input)

    fun smoothstep(from: Float, to: Float, input: ParticleNode) =
        ParticleNode.SmoothStep(Scalar(from), Scalar(to), input)

    // --- 随机与噪声 ---
    fun noise(input: ParticleNode, min: Float = 0f, max: Float = 1f, octaves: Int = 1) =
        ParticleNode.Noise(input, min, max, octaves)

    fun random(min: ParticleNode, max: ParticleNode, seed: ParticleNode? = null) =
        ParticleNode.Random(min, max, seed)

}

object ParticleNodeMath {
    val PI: ParticleNode = Scalar(kotlin.math.PI.toFloat())

    // --- Unit Conversions ---
    fun toRadians(degrees: ParticleNode): ParticleNode = (PI / Scalar(180.0f)) * degrees
    fun toRadians(degrees: Float): ParticleNode = (PI / 180.0f) * degrees

    fun toDegrees(radians: ParticleNode): ParticleNode = (Scalar(180.0f) / PI) * radians
    fun toDegrees(radians: Float): ParticleNode = (Scalar(180.0f) / PI) * radians

    // --- Vector Math Operators ---
    fun dot(left: ParticleNode, right: ParticleNode): ParticleNode = ParticleNode.Dot(left, right)
    fun dot(left: ParticleNode, right: Float): ParticleNode = ParticleNode.Dot(left, Scalar(right))

    fun length(node: ParticleNode): ParticleNode = ParticleNode.Length(node)
    fun length(value: Float): ParticleNode = ParticleNode.Length(Scalar(value))

    fun distance(p1: ParticleNode, p2: ParticleNode): ParticleNode = ParticleNode.Distance(p1, p2)
    fun distance(p1: ParticleNode, p2: Float): ParticleNode = ParticleNode.Distance(p1, Scalar(p2))
    fun distance(p1: Float, p2: ParticleNode): ParticleNode = ParticleNode.Distance(Scalar(p1), p2)

    // -- Compare --
    fun min(first: ParticleNode, second: ParticleNode): ParticleNode = ParticleNode.Min(first, second)
    fun min(first: ParticleNode, second: Float): ParticleNode = ParticleNode.Min(first, Scalar(second))
    fun min(first: Float, second: ParticleNode): ParticleNode = ParticleNode.Min(Scalar(first), second)
    fun max(first: ParticleNode, second: ParticleNode): ParticleNode = ParticleNode.Max(first, second)
    fun max(first: ParticleNode, second: Float): ParticleNode = ParticleNode.Max(first, Scalar(second))
    fun max(first: Float, second: ParticleNode): ParticleNode = ParticleNode.Max(Scalar(first), second)

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

    // --- Random ---
    fun random(min: ParticleNode, max: ParticleNode, seed: ParticleNode? = null): ParticleNode =
        ParticleNode.Random(min, max, seed)
    fun random(min: Float, max: Float, seed: ParticleNode? = null): ParticleNode =
        ParticleNode.Random(Scalar(min), Scalar(max), seed)

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

    // --- Hash ---
    fun hash(node: ParticleNode): ParticleNode = ParticleNode.Hash(node)
}