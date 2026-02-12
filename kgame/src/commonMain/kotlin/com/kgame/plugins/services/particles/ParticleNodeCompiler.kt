package com.kgame.plugins.services.particles

import com.kgame.engine.geometry.Vector2
import kotlin.math.*

/**
 * Industrial-grade compiler for ParticleNodes.
 *
 * This compiler transforms a recursive Abstract Syntax Tree (AST) into a flat,
 * optimized chain of function calls (Closures). This approach eliminates the
 * overhead of recursive class interpretation and leverages the JVM's JIT
 * inlining capabilities for maximum performance.
 *
 * Key Features:
 * - **Zero GC:** Uses value classes for Vector2 operations.
 * - **Type Promotion:** Automatically handles Scalar-to-Vector and Scalar-to-Color conversions.
 * - **branchless Logic:** Optimizes mathematical operations where possible.
 */
class ParticleNodeCompiler {

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Compiles a node tree into an executable scalar function.
     */
    fun compileScalar(node: ParticleNode): ScalarExec = traverseScalar(node)

    /**
     * Compiles a node tree into an executable vector function.
     * Guaranteed to return a value class [Vector2] without heap allocation.
     */
    fun compileVector(node: ParticleNode): VectorExec = traverseVector(node)

    /**
     * Compiles a node tree into an executable color function (Packed Int).
     */
    fun compileColor(node: ParticleNode): ColorExec = traverseColor(node)


    // =========================================================================
    // Internal: Scalar Compilation Path
    // =========================================================================

    private fun traverseScalar(node: ParticleNode): ScalarExec {
        return when (node) {
            // --- Leafs: Constants & Context ---
            is ParticleNode.Scalar -> { val v = node.value; ScalarExec { v } }
            is ParticleNode.Time -> ScalarExec { ctx -> ctx.getFloat(ParticleContext.TIME) }
            is ParticleNode.DeltaTime -> ScalarExec { ctx -> ctx.getFloat(ParticleContext.DELTA_TIME) }
            is ParticleNode.Progress -> ScalarExec { ctx -> ctx.getFloat(ParticleContext.PROGRESS) }
            is ParticleNode.Index -> ScalarExec { ctx -> ctx.getInt(ParticleContext.INDEX).toFloat() }
            is ParticleNode.Count -> ScalarExec { ctx -> ctx.getInt(ParticleContext.COUNT).toFloat() }

            // --- Arithmetic ---
            is ParticleNode.Add -> {
                val l = traverseScalar(node.left); val r = traverseScalar(node.right)
                ScalarExec { ctx -> l.eval(ctx) + r.eval(ctx) }
            }
            is ParticleNode.Subtract -> {
                val l = traverseScalar(node.left); val r = traverseScalar(node.right)
                ScalarExec { ctx -> l.eval(ctx) - r.eval(ctx) }
            }
            is ParticleNode.Multiply -> {
                val l = traverseScalar(node.left); val r = traverseScalar(node.right)
                ScalarExec { ctx -> l.eval(ctx) * r.eval(ctx) }
            }
            is ParticleNode.Divide -> {
                val l = traverseScalar(node.left); val r = traverseScalar(node.right)
                ScalarExec { ctx ->
                    val den = r.eval(ctx)
                    if (den != 0f) l.eval(ctx) / den else 0f
                }
            }

            // --- Math Functions ---
            is ParticleNode.Sin -> { val n = traverseScalar(node.node); ScalarExec { ctx -> sin(n.eval(ctx)) } }
            is ParticleNode.Cos -> { val n = traverseScalar(node.node); ScalarExec { ctx -> cos(n.eval(ctx)) } }
            is ParticleNode.Tan -> { val n = traverseScalar(node.node); ScalarExec { ctx -> tan(n.eval(ctx)) } }
            is ParticleNode.Atan -> { val n = traverseScalar(node.node); ScalarExec { ctx -> atan(n.eval(ctx)) } }
            is ParticleNode.Atan2 -> {
                val y = traverseScalar(node.y); val x = traverseScalar(node.x)
                ScalarExec { ctx -> atan2(y.eval(ctx), x.eval(ctx)) }
            }
            is ParticleNode.Pow -> {
                val b = traverseScalar(node.base); val e = traverseScalar(node.exponent)
                ScalarExec { ctx -> b.eval(ctx).pow(e.eval(ctx)) }
            }
            is ParticleNode.Sqrt -> { val n = traverseScalar(node.node); ScalarExec { ctx -> sqrt(n.eval(ctx)) } }
            is ParticleNode.Abs -> { val n = traverseScalar(node.node); ScalarExec { ctx -> abs(n.eval(ctx)) } }

            // --- Shaping Functions ---
            is ParticleNode.Fract -> {
                val n = traverseScalar(node.node)
                ScalarExec { ctx -> val v = n.eval(ctx); v - floor(v) }
            }
            is ParticleNode.Mix -> {
                val s = traverseScalar(node.start); val e = traverseScalar(node.end); val t = traverseScalar(node.t)
                ScalarExec { ctx ->
                    val factor = t.eval(ctx)
                    s.eval(ctx) * (1f - factor) + e.eval(ctx) * factor
                }
            }
            is ParticleNode.Step -> {
                val edge = traverseScalar(node.edge); val v = traverseScalar(node.v)
                ScalarExec { ctx -> if (v.eval(ctx) < edge.eval(ctx)) 0f else 1f }
            }
            is ParticleNode.SmoothStep -> {
                val e0 = traverseScalar(node.e0); val e1 = traverseScalar(node.e1); val v = traverseScalar(node.v)
                ScalarExec { ctx ->
                    val min = e0.eval(ctx); val max = e1.eval(ctx); val x = v.eval(ctx)
                    val t = ((x - min) / (max - min)).coerceIn(0f, 1f)
                    t * t * (3f - 2f * t)
                }
            }

            // --- Logic & Random ---
            is ParticleNode.Clamp -> {
                val v = traverseScalar(node.value); val min = traverseScalar(node.min); val max = traverseScalar(node.max)
                ScalarExec { ctx -> v.eval(ctx).coerceIn(min.eval(ctx), max.eval(ctx)) }
            }
            is ParticleNode.RandomRange -> {
                val min = node.min; val max = node.max; val exp = node.exp; val seed = node.hashCode()
                ScalarExec { ctx ->
                    val id = ctx.getInt(ParticleContext.INDEX)
                    val t = iHash(id xor seed)
                    val w = if (exp == 1f) t else t.pow(exp)
                    min + (max - min) * w
                }
            }

            // --- Fallbacks (Type Promotion / Demotion) ---
            is ParticleNode.Vector2 -> {
                val v = traverseVector(node)
                ScalarExec { ctx -> v.eval(ctx).x } // Extract X component
            }
            is ParticleNode.Color -> {
                val c = traverseColor(node)
                ScalarExec { ctx -> ((c.eval(ctx) ushr 24) and 0xFF) / 255f } // Extract Alpha
            }

            // --- Default ---
            else -> ScalarExec { 0f }
        }
    }


    // =========================================================================
    // Internal: Vector Compilation Path
    // =========================================================================

    private fun traverseVector(node: ParticleNode): VectorExec {
        return when (node) {
            // --- Construction ---
            is ParticleNode.Vector2 -> {
                val x = traverseScalar(node.x); val y = traverseScalar(node.y)
                VectorExec { ctx -> Vector2(x.eval(ctx), y.eval(ctx)) }
            }

            // --- Arithmetic (Component-wise) ---
            is ParticleNode.Add -> {
                val l = traverseVector(node.left); val r = traverseVector(node.right)
                VectorExec { ctx -> l.eval(ctx) + r.eval(ctx) }
            }
            is ParticleNode.Subtract -> {
                val l = traverseVector(node.left); val r = traverseVector(node.right)
                VectorExec { ctx -> l.eval(ctx) - r.eval(ctx) }
            }
            is ParticleNode.Multiply -> {
                val l = traverseVector(node.left); val r = traverseVector(node.right)
                VectorExec { ctx -> l.eval(ctx) * r.eval(ctx) }
            }
            is ParticleNode.Divide -> {
                val l = traverseVector(node.left); val r = traverseVector(node.right)
                VectorExec { ctx -> l.eval(ctx) / r.eval(ctx) }
            }

            // --- Vector Operations ---
            is ParticleNode.Mix -> {
                val s = traverseVector(node.start); val e = traverseVector(node.end); val t = traverseScalar(node.t)
                VectorExec { ctx ->
                    val v1 = s.eval(ctx); val v2 = e.eval(ctx); val factor = t.eval(ctx)
                    Vector2(
                        v1.x * (1f - factor) + v2.x * factor,
                        v1.y * (1f - factor) + v2.y * factor
                    )
                }
            }
            is ParticleNode.Normalize -> {
                val n = traverseVector(node.node)
                VectorExec { ctx ->
                    val v = n.eval(ctx)
                    val len = sqrt(v.x * v.x + v.y * v.y)
                    if (len > 0) Vector2(v.x / len, v.y / len) else Vector2(0f, 0f)
                }
            }

            // --- Complex Logic ---
            is ParticleNode.Select -> {
                val t = traverseVector(node.onTrue)
                val f = traverseVector(node.onFalse)
                val cond = compileCondition(node.condition)
                VectorExec { ctx -> if (cond.check(ctx)) t.eval(ctx) else f.eval(ctx) }
            }

            // =================================================================
            // CORE FEATURE: Implicit Scalar Promotion
            // If the node is not a vector type, verify it as a scalar and promote it.
            // =================================================================
            else -> {
                val s = traverseScalar(node)
                // Promote scalar 'v' to vector '(v, v)'
                // Since Vector2 is a value class, this allocation is on the stack (zero cost).
                VectorExec { ctx ->
                    val v = s.eval(ctx)
                    Vector2(v, v)
                }
            }
        }
    }


    // =========================================================================
    // Internal: Color Compilation Path
    // =========================================================================

    private fun traverseColor(node: ParticleNode): ColorExec {
        return when (node) {
            // --- Construction ---
            is ParticleNode.Color -> {
                val r = traverseScalar(node.red); val g = traverseScalar(node.green)
                val b = traverseScalar(node.blue); val a = traverseScalar(node.alpha)
                ColorExec { ctx ->
                    val ri = (r.eval(ctx).coerceIn(0f, 1f) * 255).toInt()
                    val gi = (g.eval(ctx).coerceIn(0f, 1f) * 255).toInt()
                    val bi = (b.eval(ctx).coerceIn(0f, 1f) * 255).toInt()
                    val ai = (a.eval(ctx).coerceIn(0f, 1f) * 255).toInt()
                    (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
                }
            }

            // --- Operations ---
            is ParticleNode.Mix -> {
                val start = traverseColor(node.start)
                val end = traverseColor(node.end)
                val time = traverseScalar(node.t)
                ColorExec { ctx ->
                    val t = time.eval(ctx).coerceIn(0f, 1f)
                    val c1 = start.eval(ctx)
                    val c2 = end.eval(ctx)
                    fastLerpColor(c1, c2, t)
                }
            }

            is ParticleNode.Select -> {
                val t = traverseColor(node.onTrue); val f = traverseColor(node.onFalse)
                val cond = compileCondition(node.condition)
                ColorExec { ctx -> if (cond.check(ctx)) t.eval(ctx) else f.eval(ctx) }
            }

            // =================================================================
            // CORE FEATURE: Implicit Scalar Promotion to Color (Grayscale)
            // =================================================================
            else -> {
                val s = traverseScalar(node)
                ColorExec { ctx ->
                    val v = (s.eval(ctx).coerceIn(0f, 1f) * 255).toInt()
                    // Result: A=255, R=v, G=v, B=v
                    (255 shl 24) or (v shl 16) or (v shl 8) or v
                }
            }
        }
    }


    // =========================================================================
    // Helpers & Utilities
    // =========================================================================

    private fun compileCondition(cond: SelectCondition): ConditionExec {
        return when (cond) {
            is SelectCondition.Ratio -> {
                val threshold = cond.value; val seed = cond.hashCode()
                ConditionExec { ctx -> iHash(ctx.getInt(ParticleContext.INDEX) xor seed) < threshold }
            }
            is SelectCondition.Threshold -> {
                val threshold = cond.value
                ConditionExec { ctx -> ctx.getInt(ParticleContext.INDEX) < threshold }
            }
            is SelectCondition.Modulo -> {
                val div = cond.divisor
                ConditionExec { ctx -> ctx.getInt(ParticleContext.INDEX) % div == 0 }
            }
        }
    }

    /**
     * Optimized color interpolation without object creation.
     */
    private fun fastLerpColor(c1: Int, c2: Int, t: Float): Int {
        if (t <= 0f) return c1
        if (t >= 1f) return c2

        val a1 = (c1 ushr 24) and 0xFF; val a2 = (c2 ushr 24) and 0xFF
        val r1 = (c1 ushr 16) and 0xFF; val r2 = (c2 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF; val g2 = (c2 ushr 8) and 0xFF
        val b1 = c1 and 0xFF; val b2 = c2 and 0xFF

        val a = (a1 + (a2 - a1) * t).toInt()
        val r = (r1 + (r2 - r1) * t).toInt()
        val g = (g1 + (g2 - g1) * t).toInt()
        val b = (b1 + (b2 - b1) * t).toInt()

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Deterministic integer hash for random stability across frames.
     * Returns a float in range [0, 1].
     */
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