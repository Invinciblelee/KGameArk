package com.kgame.plugins.services.particles

/**
 * High-performance Parser for emission patterns.
 * Directly uses the Context to resolve nodes, ensuring zero redundancy.
 */
object ParticlePatternParser : ParticleParser<VertexPattern> {
    override fun translate(scope: ParticleNodeScope): VertexPattern {
        return VertexPattern(scope)
    }
}