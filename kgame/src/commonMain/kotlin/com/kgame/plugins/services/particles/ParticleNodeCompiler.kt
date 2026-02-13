package com.kgame.plugins.services.particles

import com.kgame.engine.geometry.Vector2
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Optimized Compiler that mirrors ParticleNodeResolver logic 1:1.
 * Each closure generated here is a functional equivalent of the recursive resolver.
 */
class ParticleNodeCompiler {

    // =========================================================================
    // Public Entry Points
    // =========================================================================

    fun compileScalar(node: ParticleNode): ScalarExec = traverseScalar(node)
    fun compileVector(node: ParticleNode): VectorExec = traverseVector(node)
    fun compileColor(node: ParticleNode): ColorExec = traverseColor(node)

    // =========================================================================
    // Scalar Compilation Path (1:1 with resolveScalar)
    // =========================================================================

    private fun traverseScalar(node: ParticleNode): ScalarExec {
        return when (node) {
            // --- Constants and Uniforms ---
            is ParticleNode.Scalar -> {
                val v = node.value; ScalarExec { v }
            }

            is ParticleNode.Time -> ScalarExec { ctx -> ctx.time }
            is ParticleNode.DeltaTime -> ScalarExec { ctx -> ctx.deltaTime }
            is ParticleNode.Progress -> ScalarExec { ctx -> ctx.progress }
            is ParticleNode.Index -> ScalarExec { ctx -> ctx.index.toFloat() }
            is ParticleNode.Count -> ScalarExec { ctx -> ctx.count.toFloat() }
            is ParticleNode.Origin -> ScalarExec { ctx -> ctx.origin.x }

            // --- Arithmetic Operations ---
            is ParticleNode.Add -> {
                val l = traverseScalar(node.left);
                val r = traverseScalar(node.right)
                ScalarExec { ctx -> l.eval(ctx) + r.eval(ctx) }
            }

            is ParticleNode.Subtract -> {
                val l = traverseScalar(node.left);
                val r = traverseScalar(node.right)
                ScalarExec { ctx -> l.eval(ctx) - r.eval(ctx) }
            }

            is ParticleNode.Multiply -> {
                val l = traverseScalar(node.left);
                val r = traverseScalar(node.right)
                ScalarExec { ctx -> l.eval(ctx) * r.eval(ctx) }
            }

            is ParticleNode.Divide -> {
                val l = traverseScalar(node.left);
                val r = traverseScalar(node.right)
                ScalarExec { ctx ->
                    val rv = r.eval(ctx)
                    if (rv != 0f) l.eval(ctx) / rv else 0f
                }
            }

            // --- Trigonometric Functions ---
            is ParticleNode.Sin -> {
                val n = traverseScalar(node.node); ScalarExec { ctx -> sin(n.eval(ctx)) }
            }

            is ParticleNode.Cos -> {
                val n = traverseScalar(node.node); ScalarExec { ctx -> cos(n.eval(ctx)) }
            }

            is ParticleNode.Tan -> {
                val n = traverseScalar(node.node); ScalarExec { ctx -> tan(n.eval(ctx)) }
            }

            is ParticleNode.Atan -> {
                val n = traverseScalar(node.node); ScalarExec { ctx -> atan(n.eval(ctx)) }
            }

            is ParticleNode.Atan2 -> {
                val y = traverseScalar(node.y);
                val x = traverseScalar(node.x)
                ScalarExec { ctx -> atan2(y.eval(ctx), x.eval(ctx)) }
            }

            // --- Exponential and Power Functions ---
            is ParticleNode.Pow -> {
                val b = traverseScalar(node.base);
                val e = traverseScalar(node.exponent)
                ScalarExec { ctx -> b.eval(ctx).pow(e.eval(ctx)) }
            }

            is ParticleNode.Exp -> {
                val n = traverseScalar(node.node); ScalarExec { ctx -> exp(n.eval(ctx)) }
            }

            is ParticleNode.Sqrt -> {
                val n = traverseScalar(node.node); ScalarExec { ctx -> sqrt(n.eval(ctx)) }
            }

            is ParticleNode.Abs -> {
                val n = traverseScalar(node.node); ScalarExec { ctx -> abs(n.eval(ctx)) }
            }

            // --- Cycling and Shaping ---
            is ParticleNode.Fract -> {
                val n = traverseScalar(node.node)
                ScalarExec { ctx -> val v = n.eval(ctx); v - floor(v) }
            }

            is ParticleNode.Mod -> {
                val x = traverseScalar(node.left)
                val y = traverseScalar(node.right)
                ScalarExec { ctx ->
                    val xV = x.eval(ctx);
                    val yV = y.eval(ctx)
                    if (yV != 0f) xV - yV * floor(xV / yV) else 0f
                }
            }

            is ParticleNode.Floor -> {
                val n = traverseScalar(node.node); ScalarExec { ctx -> floor(n.eval(ctx)) }
            }

            is ParticleNode.Ceil -> {
                val n = traverseScalar(node.node); ScalarExec { ctx -> ceil(n.eval(ctx)) }
            }

            is ParticleNode.Sign -> {
                val n = traverseScalar(node.node)
                ScalarExec { ctx ->
                    val v = n.eval(ctx)
                    if (v > 0f) 1.0f else if (v < 0f) -1.0f else 0.0f
                }
            }

            // --- Interpolation and Steps ---
            is ParticleNode.Mix -> {
                val from = traverseScalar(node.from)
                val to = traverseScalar(node.to)
                val factor = traverseScalar(node.factor)
                ScalarExec { ctx ->
                    val fV = from.eval(ctx)
                    val tV = to.eval(ctx)
                    val t = factor.eval(ctx).coerceIn(0f, 1f)
                    fV * (1.0f - t) + tV * t
                }
            }

            is ParticleNode.Step -> {
                val threshold = traverseScalar(node.threshold)
                val input = traverseScalar(node.input)
                ScalarExec { ctx ->
                    if (input.eval(ctx) < threshold.eval(ctx)) 0.0f else 1.0f
                }
            }

            is ParticleNode.SmoothStep -> {
                val from = traverseScalar(node.from)
                val to = traverseScalar(node.to)
                val factor = traverseScalar(node.input)
                ScalarExec { ctx ->
                    val fV = from.eval(ctx)
                    val tV = to.eval(ctx)
                    val iV = factor.eval(ctx)
                    val t = ((iV - fV) / (tV - fV)).coerceIn(0.0f, 1.0f)
                    t * t * (3.0f - 2.0f * t)
                }
            }

            is ParticleNode.LinearStep -> {
                val from = traverseScalar(node.from)
                val to = traverseScalar(node.to)
                val factor = traverseScalar(node.input)
                ScalarExec { ctx ->
                    val fV = from.eval(ctx)
                    val tV = to.eval(ctx)
                    val iV = factor.eval(ctx)
                    if (fV == tV) 0.0f else ((iV - fV) / (tV - fV)).coerceIn(0.0f, 1.0f)
                }
            }

            is ParticleNode.Noise -> {
                val inputExec = compileScalar(node.input)
                val nodeHash = node.hashCode()
                val range = node.max - node.min
                ScalarExec { ctx ->
                    val x = inputExec.eval(ctx)

                    val i = floor(x).toInt()
                    val f = x - i

                    val v0 = iHash(i xor nodeHash)
                    val v1 = iHash((i + 1) xor nodeHash)

                    val t = f * f * (3f - 2f * f)
                    val noiseValue = v0 + (v1 - v0) * t

                    node.min + noiseValue * range
                }
            }

            // --- Comparisons and Selections ---
            is ParticleNode.Max -> {
                val f = traverseScalar(node.first)
                val s = traverseScalar(node.second)
                ScalarExec { ctx -> max(f.eval(ctx), s.eval(ctx)) }
            }

            is ParticleNode.Min -> {
                val f = traverseScalar(node.first)
                val s = traverseScalar(node.second)
                ScalarExec { ctx -> min(f.eval(ctx), s.eval(ctx)) }
            }

            is ParticleNode.Clamp -> {
                val v = traverseScalar(node.value)
                val minN = traverseScalar(node.min)
                val maxN = traverseScalar(node.max)
                ScalarExec { ctx -> v.eval(ctx).coerceIn(minN.eval(ctx), maxN.eval(ctx)) }
            }

            is ParticleNode.Random -> {
                val minExec = compileScalar(node.min)
                val maxExec = compileScalar(node.max)

                val seedExec = node.seed?.let { compileScalar(it) }
                val nodeHash = node.hashCode()

                ScalarExec { ctx ->
                    val t = seedExec?.eval(ctx) ?: iHash(ctx.index xor nodeHash)
                    val currentMin = minExec.eval(ctx)
                    val currentMax = maxExec.eval(ctx)
                    currentMin + (currentMax - currentMin) * t
                }
            }

            is ParticleNode.Comparison -> {
                val l = traverseScalar(node.left)
                val r = traverseScalar(node.right)
                val op = node.op
                ScalarExec { ctx ->
                    val a = l.eval(ctx);
                    val b = r.eval(ctx)
                    val res = when (op) {
                        ComparisonOp.Equal -> a == b
                        ComparisonOp.NotEqual -> a != b
                        ComparisonOp.GreaterThan -> a > b
                        ComparisonOp.GreaterEqual -> a >= b
                        ComparisonOp.LessThan -> a < b
                        ComparisonOp.LessEqual -> a <= b
                    }
                    if (res) 1.0f else 0.0f
                }
            }

            is ParticleNode.Combine -> {
                val lN = traverseScalar(node.left);
                val rN = traverseScalar(node.right);
                val op = node.op
                ScalarExec { ctx ->
                    val l = lN.eval(ctx) > 0.5f;
                    val r = rN.eval(ctx) > 0.5f
                    val res = when (op) {
                        CombineOp.And -> l && r
                        CombineOp.Or -> l || r
                    }
                    if (res) 1.0f else 0.0f
                }
            }

            is ParticleNode.Select -> {
                val cond = compileCondition(node.condition)
                val t = traverseScalar(node.onTrue)
                val f = traverseScalar(node.onFalse)
                ScalarExec { ctx -> if (cond.check(ctx)) t.eval(ctx) else f.eval(ctx) }
            }

            // --- Vector-to-Scalar Operations ---
            is ParticleNode.Dot -> {
                val l = traverseVector(node.left)
                val r = traverseVector(node.right)
                ScalarExec { ctx ->
                    val lv = l.eval(ctx);
                    val rv = r.eval(ctx)
                    lv.x * rv.x + lv.y * rv.y
                }
            }

            is ParticleNode.Length -> {
                val n = traverseVector(node.node)
                ScalarExec { ctx ->
                    val v = n.eval(ctx)
                    sqrt(v.x * v.x + v.y * v.y)
                }
            }

            is ParticleNode.Distance -> {
                val p1N = traverseVector(node.p1)
                val p2N = traverseVector(node.p2)
                ScalarExec { ctx ->
                    val p1 = p1N.eval(ctx);
                    val p2 = p2N.eval(ctx)
                    val dx = p1.x - p2.x;
                    val dy = p1.y - p2.y
                    sqrt(dx * dx + dy * dy)
                }
            }

            // --- Type Promotion Fallbacks ---
            is ParticleNode.Vector2 -> {
                val x = traverseScalar(node.x)
                ScalarExec { ctx -> x.eval(ctx) }
            }

            is ParticleNode.Color -> {
                val a = traverseScalar(node.alpha)
                ScalarExec { ctx -> a.eval(ctx) }
            }

            // --- Logic ---
            is ParticleNode.Not -> {
                val n = traverseScalar(node.node)
                ScalarExec { ctx ->
                    if (n.eval(ctx) > 0.5f) 0.0f else 1.0f
                }
            }

            is ParticleNode.Component -> {
                val vN = traverseVector(node.node)
                val index = node.index
                ScalarExec { ctx ->
                    val v = vN.eval(ctx)
                    if (index == 0) v.x else v.y
                }
            }

            is ParticleNode.Sample -> {
                val func = traverseScalar(node.function)
                val param = traverseScalar(node.parameter)
                ScalarExec { ctx ->
                    val p = param.eval(ctx)

                    val oldTime = ctx.time
                    val oldProgress = ctx.progress

                    ctx.time = p
                    ctx.progress = p

                    val result = func.eval(ctx)

                    ctx.time = oldTime
                    ctx.progress = oldProgress

                    result
                }
            }

            is ParticleNode.Hash -> {
                val n = traverseScalar(node.node)
                val nodeSeed = node.hashCode()
                ScalarExec { ctx ->
                    val v = n.eval(ctx)
                    iHash(v.toRawBits() xor nodeSeed)
                }
            }
        }
    }

    // =========================================================================
    // Vector Compilation Path (1:1 with resolveVector2)
    // =========================================================================

    private fun traverseVector(node: ParticleNode): VectorExec = when (node) {
        is ParticleNode.Origin -> VectorExec { ctx -> Vector2(ctx.origin.packedValue) }

        is ParticleNode.Scalar -> {
            val v = node.value
            VectorExec { Vector2(v, v) }
        }

        is ParticleNode.Vector2 -> {
            val x = traverseScalar(node.x)
            val y = traverseScalar(node.y)
            VectorExec { ctx -> Vector2(x.eval(ctx), y.eval(ctx)) }
        }

        is ParticleNode.Add -> {
            val l = traverseVector(node.left)
            val r = traverseVector(node.right)
            VectorExec { ctx ->
                val lv = l.eval(ctx);
                val rv = r.eval(ctx)
                Vector2(lv.x + rv.x, lv.y + rv.y)
            }
        }

        is ParticleNode.Subtract -> {
            val l = traverseVector(node.left)
            val r = traverseVector(node.right)
            VectorExec { ctx ->
                val lv = l.eval(ctx);
                val rv = r.eval(ctx)
                Vector2(lv.x - rv.x, lv.y - rv.y)
            }
        }

        is ParticleNode.Multiply -> {
            val l = traverseVector(node.left)
            val r = traverseVector(node.right)
            VectorExec { ctx ->
                val lv = l.eval(ctx);
                val rv = r.eval(ctx)
                Vector2(lv.x * rv.x, lv.y * rv.y)
            }
        }

        is ParticleNode.Divide -> {
            val l = traverseVector(node.left)
            val r = traverseVector(node.right)
            VectorExec { ctx ->
                val lv = l.eval(ctx);
                val rv = r.eval(ctx)
                Vector2(
                    if (rv.x != 0f) lv.x / rv.x else 0f,
                    if (rv.y != 0f) lv.y / rv.y else 0f
                )
            }
        }

        is ParticleNode.Select -> {
            val cond = compileCondition(node.condition)
            val t = traverseVector(node.onTrue)
            val f = traverseVector(node.onFalse)
            VectorExec { ctx -> if (cond.check(ctx)) t.eval(ctx) else f.eval(ctx) }
        }

        is ParticleNode.Sample -> {
            val func = traverseVector(node.function)
            val param = traverseScalar(node.parameter)
            VectorExec { ctx ->
                val p = param.eval(ctx)

                val oldTime = ctx.time
                val oldProgress = ctx.progress

                ctx.time = p
                ctx.progress = p

                val result = func.eval(ctx)

                ctx.time = oldTime
                ctx.progress = oldProgress

                result
            }
        }

        else -> {
            val s = traverseScalar(node)
            VectorExec { ctx -> val v = s.eval(ctx); Vector2(v, v) }
        }
    }

    // =========================================================================
    // Color Compilation Path (1:1 with resolveColor)
    // =========================================================================

    private fun traverseColor(node: ParticleNode): ColorExec = when (node) {
        is ParticleNode.Color -> {
            val rN = traverseScalar(node.red);
            val gN = traverseScalar(node.green)
            val bN = traverseScalar(node.blue);
            val aN = traverseScalar(node.alpha)
            ColorExec { ctx ->
                val r = (rN.eval(ctx).coerceIn(0f, 1f) * 255).toInt()
                val g = (gN.eval(ctx).coerceIn(0f, 1f) * 255).toInt()
                val b = (bN.eval(ctx).coerceIn(0f, 1f) * 255).toInt()
                val a = (aN.eval(ctx).coerceIn(0f, 1f) * 255).toInt()
                (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        is ParticleNode.Select -> {
            val cond = compileCondition(node.condition)
            val t = traverseColor(node.onTrue);
            val f = traverseColor(node.onFalse)
            ColorExec { ctx -> if (cond.check(ctx)) t.eval(ctx) else f.eval(ctx) }
        }

        else -> {
            val s = traverseScalar(node)
            ColorExec { ctx ->
                val v = (s.eval(ctx).coerceIn(0f, 1f) * 255f).toInt()
                (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
    }

    // =========================================================================
    // Internal Utilities
    // =========================================================================

    private fun compileCondition(node: ParticleNode): ConditionExec {
        val exec = traverseScalar(node)
        return ConditionExec { ctx ->
            exec.eval(ctx) > 0.5f
        }
    }

    private fun iHash(x: Int): Float {
        var h = x
        h = h xor (h ushr 16)
        h *= -0x7a143595
        h = h xor (h ushr 13)
        h *= -0x3d4d51cb
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) * 4.656613E-10f
    }
}