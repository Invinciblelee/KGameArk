package com.kgame.plugins.services.particles

/**
 * High-performance Parser for emission patterns.
 * Directly uses the Context to resolve nodes, ensuring zero redundancy.
 */
object ParticleVertexParser : ParticleParser<List<VertexEffect>> {
    override fun translate(scope: ParticleNodeScope): List<VertexEffect> {
        return scope.layers.map { layer ->
            val pattern = VertexPattern(layer, scope.context)
            VertexEffect(pattern).also { effect ->
                effect.setResolution(layer.frame.size)
            }
        }
    }
}