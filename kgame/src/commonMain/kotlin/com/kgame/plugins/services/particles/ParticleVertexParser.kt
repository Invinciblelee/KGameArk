package com.kgame.plugins.services.particles

import com.kgame.engine.graphics.material.MaterialEffect

/**
 * High-performance Parser for emission patterns.
 * Directly uses the Context to resolve nodes, ensuring zero redundancy.
 */
object ParticleVertexParser : ParticleParser<List<ParticleRenderer>> {
    override fun translate(scope: ParticleNodeScope): List<ParticleRenderer> {
        return scope.layers.map { layer ->
            val pattern = VertexPattern(layer, layer.context)
            val effect = layer.material?.let { MaterialEffect(it) }
            ParticleRenderer(pattern, effect, layer.frame, layer.duration).also { effect ->
                effect.setResolution(layer.frame.size)
            }
        }
    }
}