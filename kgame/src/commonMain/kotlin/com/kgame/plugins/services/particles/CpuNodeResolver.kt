package com.kgame.plugins.services.particles

import com.kgame.engine.geometry.Vector2
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

        is ParticleNode.Resolution -> {
            val resolution = context.getVector2(ParticleContext.RESOLUTION)
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
        is ParticleNode.Atan -> atan(resolveScalar(node.node, context))
        is ParticleNode.Atan2 -> atan2(resolveScalar(node.y, context), resolveScalar(node.x, context))

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
        is ParticleNode.Combine -> {
            val l = resolveScalar(node.left, context) > 0.5f
            val r = resolveScalar(node.right, context) > 0.5f
            val res = when (node.op) {
                CombineOp.And -> l && r
                CombineOp.Or -> l || r
            }
            if (res) 1.0f else 0.0f
        }
        is ParticleNode.Select -> {
            if (evalCondition(node.condition, context)) resolveScalar(node.onTrue, context)
            else resolveScalar(node.onFalse, context)
        }

        // --- Vector-to-Scalar Operations ---
        is ParticleNode.Dot -> {
            val l = resolveVector2(node.left, context)
            val r = resolveVector2(node.right, context)
            l.x * r.x + l.y * r.y
        }
        is ParticleNode.Length -> {
            val v = resolveVector2(node.node, context)
            sqrt(v.x * v.x + v.y * v.y)
        }
        is ParticleNode.Distance -> {
            val p1 = resolveVector2(node.p1, context)
            val p2 = resolveVector2(node.p2, context)
            val dx = p1.x - p2.x
            val dy = p1.y - p2.y
            sqrt(dx * dx + dy * dy)
        }
        is ParticleNode.Normalize -> {
            val v = resolveVector2(node.node, context)
            v.x
        }

        // --- Type Promotion Fallbacks ---
        is ParticleNode.Vector2 -> resolveScalar(node.x, context)
        is ParticleNode.Color -> resolveScalar(node.alpha, context)
        is SlotNode -> context.getFloat(node.key, node.mapping)
    }

    /**
     * Resolves a 2D vector from any ParticleNode.
     */
    fun resolveVector2(
        node: ParticleNode,
        context: ParticleContext
    ): Vector2 = when (node) {
        is ParticleNode.Resolution -> context.getVector2(ParticleContext.RESOLUTION)

        is ParticleNode.Scalar -> {
            val v = node.value
            Vector2(v, v)
        }

        is ParticleNode.Vector2 -> Vector2(
            resolveScalar(node.x, context),
            resolveScalar(node.y, context)
        )
        is ParticleNode.Add -> {
            val l = resolveVector2(node.left, context)
            val r = resolveVector2(node.right, context)
            Vector2(l.x + r.x, l.y + r.y)
        }
        is ParticleNode.Subtract -> {
            val l = resolveVector2(node.left, context)
            val r = resolveVector2(node.right, context)
            Vector2(l.x - r.x, l.y - r.y)
        }
        is ParticleNode.Multiply -> {
            val l = resolveVector2(node.left, context)
            val r = resolveVector2(node.right, context)
            Vector2(l.x * r.x, l.y * r.y)
        }
        is ParticleNode.Divide -> {
            val l = resolveVector2(node.left, context)
            val r = resolveVector2(node.right, context)
            Vector2(
                if (r.x != 0f) l.x / r.x else 0f,
                if (r.y != 0f) l.y / r.y else 0f
            )
        }
        is ParticleNode.Mix -> {
            val s = resolveVector2(node.start, context)
            val e = resolveVector2(node.end, context)
            val t = resolveScalar(node.t, context)
            Vector2(s.x * (1f - t) + e.x * t, s.y * (1f - t) + e.y * t)
        }
        is ParticleNode.Select -> {
            if (evalCondition(node.condition, context)) resolveVector2(node.onTrue, context)
            else resolveVector2(node.onFalse, context)
        }

        // --- Vector-to-Vector Operations ---
        is ParticleNode.Normalize -> {
            val v = resolveVector2(node.node, context)
            val len = sqrt(v.x * v.x + v.y * v.y)
            if (len > 0f) Vector2(v.x / len, v.y / len) else Vector2(0f, 0f)
        }

        else -> {
            val v = resolveScalar(node, context)
            Vector2(v, v)
        }
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
        is ParticleNode.Mix -> {
            val c1 = resolveColor(node.start, context)
            val c2 = resolveColor(node.end, context)
            val t = resolveScalar(node.t, context).coerceIn(0f, 1f)

            if (t <= 0f) c1
            else if (t >= 1f) c2
            else {
                val a1 = (c1 ushr 24); val a2 = (c2 ushr 24)
                val r1 = (c1 ushr 16) and 0xFF; val r2 = (c2 ushr 16) and 0xFF
                val g1 = (c1 ushr 8) and 0xFF; val g2 = (c2 ushr 8) and 0xFF
                val b1 = c1 and 0xFF; val b2 = c2 and 0xFF

                val a = (a1 + (a2 - a1) * t).toInt()
                val r = (r1 + (r2 - r1) * t).toInt()
                val g = (g1 + (g2 - g1) * t).toInt()
                val b = (b1 + (b2 - b1) * t).toInt()

                (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        is ParticleNode.Select -> {
            if (evalCondition(node.condition, context)) resolveColor(node.onTrue, context)
            else resolveColor(node.onFalse, context)
        }
        is SlotNode -> context.getInt(node.key, node.mapping)
        else -> {
            val v = (resolveScalar(node, context).coerceIn(0f, 1f) * 255f).toInt()
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
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