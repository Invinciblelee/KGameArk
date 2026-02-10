package com.kgame.plugins.services.particles

/**
 * High-performance Parser for emission patterns.
 * Directly uses the Context to resolve nodes, ensuring zero redundancy.
 */
object ParticlePatternParser : ParticleParser<ParticlePattern> {
    override fun translate(scope: ParticleNodeScope): ParticlePattern {
        return ParticlePattern(scope)
    }
}