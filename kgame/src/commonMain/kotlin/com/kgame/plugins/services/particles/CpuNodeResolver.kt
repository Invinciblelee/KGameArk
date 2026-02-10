package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Offset
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

object CpuNodeResolver {

    /**
     * Resolves a scalar value based on current state.
     * elapsedTime is passed directly to remove coupling with Context.
     */
    fun resolveScalar(
        node: ParticleNode,
        args: ParticleArgs
    ): Float = when (node) {
        is ParticleNode.Scalar -> node.value
        is ParticleNode.Time -> args.getFloat(ParticleArgs.TIME)
        is ParticleNode.Progress -> args.getFloat(ParticleArgs.PROGRESS)
        is ParticleNode.Index -> args.getInt(ParticleArgs.INDEX).toFloat()

        is ParticleNode.RandomRange -> {
            val id = args.getInt(ParticleArgs.INDEX)
            val t = iHash(id xor node.hashCode())
            val weight = if (node.exp == 1.0f) t else t.pow(node.exp)
            node.min + (node.max - node.min) * weight
        }

        is ParticleNode.Select -> {
            val id = args.getInt(ParticleArgs.INDEX)
            val total = args.getInt(ParticleArgs.COUNT)

            val result = when (val cond = node.condition) {
                is SelectCondition.Ratio -> (id.toFloat() / total) < cond.value
                is SelectCondition.Threshold -> id < cond.value
                is SelectCondition.Modulo -> id % cond.divisor == 0
            }

            if (result) resolveScalar(node.onTrue, args)
            else resolveScalar(node.onFalse, args)
        }

        is ParticleNode.Add -> {
            resolveScalar(node.left, args) + resolveScalar(node.right, args)
        }
        
        is ParticleNode.Multiply -> {
            resolveScalar(node.left, args) * resolveScalar(node.right, args)
        }

        is ParticleNode.Vector2 -> resolveScalar(node.x, args)
        is ParticleNode.Color -> ((node.argb ushr 24) and 0xFF) / 255f
    }

    /**
     * Resolves a 2D vector (e.g., for initial position).
     * Returns a simple Offset or a custom Vector2 result.
     */
    fun resolveVector2(
        node: ParticleNode,
        args: ParticleArgs
    ): Offset = when (node) {
        // Direct vector node
        is ParticleNode.Vector2 -> Offset(
            resolveScalar(node.x, args),
            resolveScalar(node.y, args)
        )

        // Add support: Useful for "origin + randomOffset"
        is ParticleNode.Add -> {
            val left = resolveVector2(node.left, args)
            val right = resolveVector2(node.right, args)
            Offset(left.x + right.x, left.y + right.y)
        }

        // Select support: Choose between two positions
        is ParticleNode.Select -> {
            val id = args.getInt(ParticleArgs.INDEX)
            val total = args.getInt(ParticleArgs.COUNT)
            val result = when (val cond = node.condition) {
                is SelectCondition.Ratio -> (id.toFloat() / total) < cond.value
                is SelectCondition.Threshold -> id < cond.value
                is SelectCondition.Modulo -> id % cond.divisor == 0
            }
            if (result) resolveVector2(node.onTrue, args)
            else resolveVector2(node.onFalse, args)
        }

        // Fallback: Use scalar value for both X and Y
        else -> {
            val v = resolveScalar(node, args)
            Offset(v, v)
        }
    }

    /**
     * Resolves a color to a 32-bit ARGB integer.
     */
    fun resolveColor(
        node: ParticleNode,
        args: ParticleArgs
    ): Int = when (node) {
        is ParticleNode.Color -> node.argb
        is ParticleNode.Select -> {
            val id = args.getInt(ParticleArgs.INDEX)
            val total = args.getInt(ParticleArgs.COUNT)

            val result = when (val cond = node.condition) {
                is SelectCondition.Ratio -> (id.toFloat() / total) < cond.value
                is SelectCondition.Threshold -> id < cond.value
                is SelectCondition.Modulo -> id % cond.divisor == 0
            }

            if (result) resolveColor(node.onTrue, args)
            else resolveColor(node.onFalse, args)
        }
        else -> {
            // Fallback: use scalar value to generate a grayscale ARGB
            val v = (resolveScalar(node, args).coerceIn(0f, 1f) * 255).toInt()
            (v shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    private fun iHash(x: Int): Float {
        var h = x
        h = h xor (h ushr 16)
        h *= 0x85ebca6b.toInt()
        h = h xor (h ushr 13)
        h *= 0xc2b2ae35.toInt()
        h = h xor (h ushr 16)

        // 映射到 0.0 ~ 1.0
        return (h and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat()
    }
}