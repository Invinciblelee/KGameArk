package com.kgame.plugins.services.particles

internal interface ParticleNodeEvaluator {
    fun updateContext(index: Int, time: Float, progress: Float)
    fun evaluateScalar(node: ParticleNode): Float
    fun evaluateColor(node: ParticleNode): Int
}