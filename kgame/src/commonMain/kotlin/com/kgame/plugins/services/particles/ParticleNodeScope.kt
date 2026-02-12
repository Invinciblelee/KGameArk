package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Rect
import com.kgame.engine.graphics.material.Material

/**
 * The top-level scope for defining a multi-layered particle system.
 * Translates into both a Pattern for birth and a Shader for evolution.
 */
class ParticleNodeScope {
    // List of layers, each with its own independent logic tree
    val layers = mutableListOf<ParticleLayer>()

    /**
     * Defines a new particle layer within the system.
     */
    fun layer(name: String, count: Int, frame: Rect, block: ParticleLayer.() -> Unit) {
        val layer = ParticleLayer(name, count, frame).apply(block)
        layers.add(layer)
    }
}

/**
 * Represents a single layer's configuration.
 * Contains the nodes that will be resolved by the Pattern and Shader parsers.
 */
class ParticleLayer(
    val name: String,
    val count: Int,
    val frame: Rect,
    val context: ParticleContext = ParticleContext()
) : ParticleNodeProvider {
    var duration: Float = 1.0f

    var material: Material? = null

    // Physics nodes (CPU initially, GPU evolution)
    var position: ParticleNode = vec2(0f, 0f)

    // Aesthetic nodes (GPU driven)
    var size: ParticleNode = scalar(1f)

    var color: ParticleNode = color(0xFFFFFFFF)
}

/** Entry point for the multi-layer DSL. */
fun particles(block: ParticleNodeScope.() -> Unit): ParticleNodeScope {
    return ParticleNodeScope().apply(block)
}