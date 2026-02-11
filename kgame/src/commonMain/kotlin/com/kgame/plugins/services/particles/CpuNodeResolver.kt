package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*

object CpuNodeResolver {

    /**
     * Resolves a scalar value from any ParticleNode.
     */
    fun resolveScalar(
        node: ParticleNode,
        context: ParticleContext
    ): Float = when (node) {
        // --- Constants and Uniforms ---
        is ParticleNode.Scalar -> node.value
        is ParticleNode.Time -> context.getFloat(ParticleContext.TIME)
        is ParticleNode.DeltaTime -> context.getFloat(ParticleContext.DELTA_TIME)
        is ParticleNode.Progress -> context.getFloat(ParticleContext.PROGRESS)
        is ParticleNode.Index -> context.getInt(ParticleContext.INDEX).toFloat()
        is ParticleNode.Count -> context.getInt(ParticleContext.COUNT).toFloat()

        is ParticleNode.Origin -> {
            val origin = context.getOffset(ParticleContext.ORIGIN)
            origin.x
        }

        is ParticleNode.Resolution -> {
            val resolution = context.getOffset(ParticleContext.RESOLUTION)
            resolution.x
        }

        // --- Arithmetic Operations ---
        is ParticleNode.Add -> resolveScalar(node.left, context) + resolveScalar(node.right, context)
        is ParticleNode.Subtract -> resolveScalar(node.left, context) - resolveScalar(node.right, context)
        is ParticleNode.Multiply -> resolveScalar(node.left, context) * resolveScalar(node.right, context)
        is ParticleNode.Divide -> {
            val l = resolveScalar(node.left, context)
            val r = resolveScalar(node.right, context)
            if (r != 0f) l / r else 0f
        }

        // --- Trigonometric Functions ---
        is ParticleNode.Sin -> sin(resolveScalar(node.node, context))
        is ParticleNode.Cos -> cos(resolveScalar(node.node, context))
        is ParticleNode.Tan -> tan(resolveScalar(node.node, context))

        // --- Exponential and Power Functions ---
        is ParticleNode.Pow -> resolveScalar(node.base, context).pow(resolveScalar(node.exponent, context))
        is ParticleNode.Exp -> exp(resolveScalar(node.node, context))
        is ParticleNode.Sqrt -> sqrt(resolveScalar(node.node, context))
        is ParticleNode.Abs -> abs(resolveScalar(node.node, context))

        // --- Cycling and Shaping ---
        is ParticleNode.Fract -> {
            val v = resolveScalar(node.node, context)
            v - floor(v)
        }
        is ParticleNode.Mod -> {
            val x = resolveScalar(node.left, context)
            val y = resolveScalar(node.right, context)
            if (y != 0f) x - y * floor(x / y) else 0f
        }
        is ParticleNode.Floor -> floor(resolveScalar(node.node, context))
        is ParticleNode.Ceil -> ceil(resolveScalar(node.node, context))
        is ParticleNode.Sign -> {
            val v = resolveScalar(node.node, context)
            when {
                v > 0f -> 1.0f
                v < 0f -> -1.0f
                else -> 0.0f
            }
        }

        // --- Interpolation and Steps ---
        is ParticleNode.Mix -> {
            val s = resolveScalar(node.start, context)
            val e = resolveScalar(node.end, context)
            val t = resolveScalar(node.t, context)
            s * (1.0f - t) + e * t
        }
        is ParticleNode.Step -> {
            val edge = resolveScalar(node.edge, context)
            val v = resolveScalar(node.v, context)
            if (v < edge) 0.0f else 1.0f
        }
        is ParticleNode.SmoothStep -> {
            val e0 = resolveScalar(node.e0, context)
            val e1 = resolveScalar(node.e1, context)
            val v = resolveScalar(node.v, context)
            val t = ((v - e0) / (e1 - e0)).coerceIn(0.0f, 1.0f)
            t * t * (3.0f - 2.0f * t)
        }

        // --- Comparisons and Selections ---
        is ParticleNode.Max -> max(resolveScalar(node.first, context), resolveScalar(node.second, context))
        is ParticleNode.Min -> min(resolveScalar(node.first, context), resolveScalar(node.second, context))
        is ParticleNode.Clamp -> {
            val v = resolveScalar(node.value, context)
            val minV = resolveScalar(node.min, context)
            val maxV = resolveScalar(node.max, context)
            v.coerceIn(minV, maxV)
        }
        is ParticleNode.RandomRange -> {
            val id = context.getInt(ParticleContext.INDEX)
            val t = iHash(id xor node.hashCode())
            val weight = if (node.exp == 1.0f) t else t.pow(node.exp)
            node.min + (node.max - node.min) * weight
        }
        is ParticleNode.Comparison -> {
            val a = resolveScalar(node.left, context)
            val b = resolveScalar(node.right, context)
            val res = when (node.op) {
                ComparisonOp.Equal -> a == b
                ComparisonOp.NotEqual -> a != b
                ComparisonOp.GreaterThan -> a > b
                ComparisonOp.GreaterEqual -> a >= b
                ComparisonOp.LessThan -> a < b
                ComparisonOp.LessEqual -> a <= b
            }
            if (res) 1.0f else 0.0f
        }
        is ParticleNode.Select -> {
            if (evalCondition(node.condition, context)) resolveScalar(node.onTrue, context)
            else resolveScalar(node.onFalse, context)
        }

        // --- Type Promotion Fallbacks ---
        is ParticleNode.Vector2 -> resolveScalar(node.x, context)
        is ParticleNode.Color -> resolveScalar(node.alpha, context)
        is SlotNode -> {
            val raw = context.getLong(node.key)
            node.extract(raw)
        }
    }

    /**
     * Resolves a 2D vector from any ParticleNode.
     */
    fun resolveVector2(
        node: ParticleNode,
        context: ParticleContext
    ): Offset = when (node) {
        is ParticleNode.Origin -> context.getOffset(ParticleContext.ORIGIN)

        is ParticleNode.Resolution -> context.getOffset(ParticleContext.RESOLUTION)

        is ParticleNode.Vector2 -> Offset(
            resolveScalar(node.x, context),
            resolveScalar(node.y, context)
        )
        is ParticleNode.Add -> {
            val l = resolveVector2(node.left, context)
            val r = resolveVector2(node.right, context)
            Offset(l.x + r.x, l.y + r.y)
        }
        is ParticleNode.Subtract -> {
            val l = resolveVector2(node.left, context)
            val r = resolveVector2(node.right, context)
            Offset(l.x - r.x, l.y - r.y)
        }
        is ParticleNode.Multiply -> {
            val l = resolveVector2(node.left, context)
            val r = resolveVector2(node.right, context)
            Offset(l.x * r.x, l.y * r.y)
        }
        is ParticleNode.Divide -> {
            val l = resolveVector2(node.left, context)
            val r = resolveVector2(node.right, context)
            Offset(l.x / r.x, l.y / r.y)
        }
        is ParticleNode.Mix -> {
            val s = resolveVector2(node.start, context)
            val e = resolveVector2(node.end, context)
            val t = resolveScalar(node.t, context)
            Offset(s.x * (1f - t) + e.x * t, s.y * (1f - t) + e.y * t)
        }
        is ParticleNode.Select -> {
            if (evalCondition(node.condition, context)) resolveVector2(node.onTrue, context)
            else resolveVector2(node.onFalse, context)
        }
        else -> error("Unsupported node type: $node")
    }

    /**
     * Resolves a 32-bit ARGB color from any ParticleNode.
     */
    fun resolveColor(
        node: ParticleNode,
        context: ParticleContext
    ): Int = when (node) {
        is ParticleNode.Color -> {
            val r = (resolveScalar(node.red, context).coerceIn(0f, 1f) * 255).toInt()
            val g = (resolveScalar(node.green, context).coerceIn(0f, 1f) * 255).toInt()
            val b = (resolveScalar(node.blue, context).coerceIn(0f, 1f) * 255).toInt()
            val a = (resolveScalar(node.alpha, context).coerceIn(0f, 1f) * 255).toInt()
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        is ParticleNode.Select -> {
            if (evalCondition(node.condition, context)) resolveColor(node.onTrue, context)
            else resolveColor(node.onFalse, context)
        }
        is SlotNode -> {
            context.getColor(node.key).toArgb()
        }
        else -> error("Unsupported node type: $node")
    }

    private fun evalCondition(cond: SelectCondition, args: ParticleContext): Boolean {
        val id = args.getInt(ParticleContext.INDEX)
        return when (cond) {
            is SelectCondition.Ratio -> iHash(id xor cond.hashCode()) < cond.value
            is SelectCondition.Threshold -> id < cond.value
            is SelectCondition.Modulo -> id % cond.divisor == 0
        }
    }

    private fun iHash(x: Int): Float {
        var h = x
        h = h xor (h ushr 16)
        h *= 0x85ebca6b.toInt()
        h = h xor (h ushr 13)
        h *= 0xc2b2ae35.toInt()
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF).toFloat() / 2.1474836E9f
    }
}