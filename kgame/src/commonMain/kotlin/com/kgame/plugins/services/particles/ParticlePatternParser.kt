package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.kgame.engine.math.radians
import kotlin.math.pow

object ParticlePatternParser : ParticleParser<ParticlePattern> {
    override fun translate(scope: ParticleNodeScope): ParticlePattern {
        return ParticlePattern { buffer ->
            val random = kotlin.random.Random.Default
            
            scope.layers.forEach { layer ->
                repeat(layer.count) { i ->
                    val resolver = CpuNodeResolver(i, random)
                    
                    // Resolve initial physics state at birth (t=0)
                    val speed = resolver.resolve(layer.velocity)
                    val angleRad = radians(resolver.resolve(layer.angle).toDouble()).toFloat()
                    
                    buffer.putQuad(
                        position = (layer.position as ParticleNode.Vector2).let { Offset(it.x, it.y) },
                        velocity = Offset(kotlin.math.cos(angleRad) * speed, kotlin.math.sin(angleRad) * speed),
                        life = layer.duration,
                        friction = resolver.resolve(layer.friction),
                        width = resolver.resolve(layer.size), // Initial size snapshot
                        color = resolver.resolveColor(layer.color)
                    )
                }
            }
        }
    }
}

/**
 * Evaluates the Node tree on the CPU using KMP-compliant math libraries.
 * Responsible for the 'Birth' state (t=0).
 */
private class CpuNodeResolver(
    private val particleIndex: Int,
    private val random: kotlin.random.Random
) {
    /**
     * Recursively resolves a ParticleNode into a Float.
     * All math operations use kotlin.math for cross-platform compatibility.
     */
    fun resolve(node: ParticleNode): Float = when (node) {
        is ParticleNode.Scalar -> node.value

        is ParticleNode.RandomRange -> {
            // Evaluates random distribution for the birth state
            val t = random.nextFloat().let {
                if (node.exp == 1.0f) it else it.pow(node.exp)
            }
            node.min + (node.max - node.min) * t
        }

        is ParticleNode.IndexMod -> {
            if (particleIndex % node.divisor == 0) resolve(node.onTrue) else resolve(node.onFalse)
        }

        is ParticleNode.Add -> resolve(node.left) + resolve(node.right)

        is ParticleNode.Multiply -> resolve(node.left) * resolve(node.right)

        // --- Contextual Values ---
        ParticleNode.Index -> particleIndex.toFloat()

        // At birth, time and progress are strictly zero
        ParticleNode.Time -> 0.0f

        ParticleNode.Progress -> 0.0f

        is ParticleNode.Vector2 -> 0.0f // Handled component-wise in Parser

        else -> 0.0f
    }


    /**
     * Resolves a ParticleNode into an ARGB Int.
     */
    fun resolveColor(node: ParticleNode): Color = when (node) {
        is ParticleNode.Color -> Color(node.argb)

        is ParticleNode.IndexMod -> {
            if (particleIndex % node.divisor == 0) resolveColor(node.onTrue)
            else resolveColor(node.onFalse)
        }

        // Fallback for non-color nodes used in color context
        else -> Color.White
    }
}