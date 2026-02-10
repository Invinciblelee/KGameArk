package com.kgame.plugins.services.particles

/**
 * A generic parser interface that translates a particle logic blueprint
 * into a specific executable target (e.g., CPU Pattern or GPU Shader).
 */
interface ParticleParser<out T> {
    /**
     * Translates the provided [ParticleNodeScope] into the target type [T].
     * @param scope The root scope containing all defined layers and nodes.
     * @return An instance of [T] representing the executable logic.
     */
    fun translate(scope: ParticleNodeScope): T
}