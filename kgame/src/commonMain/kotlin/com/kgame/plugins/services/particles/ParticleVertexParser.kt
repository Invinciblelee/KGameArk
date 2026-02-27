package com.kgame.plugins.services.particles

/**
 * High-performance Parser for emission patterns.
 * Directly uses the Context to resolve nodes, ensuring zero redundancy.
 */
object ParticleVertexParser : ParticleParser<List<ParticleLayerRenderer>> {
    override fun translate(scope: ParticleNodeScope): List<ParticleLayerRenderer> {
        return scope.layers.map { layer ->
            val pattern = VertexPattern(layer, layer.context)
            ParticleLayerRenderer(pattern, layer.config)
        }
    }
}