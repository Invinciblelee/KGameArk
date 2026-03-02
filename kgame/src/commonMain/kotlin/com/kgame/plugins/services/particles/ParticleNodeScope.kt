@file:OptIn(ExperimentalMaterialVisuals::class)

package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Offset
import com.kgame.engine.graphics.material.Material

/**
 * The top-level scope for defining a multi-layered particle system.
 * Translates into both a Pattern for birth and a Shader for evolution.
 */
@ParticleDslMarker
class ParticleNodeScope {
    // List of layers, each with its own independent logic tree
    val layers = mutableListOf<ParticleLayer>()

    /**
     * Defines a new particle layer within the system.
     */
    fun layer(
        name: String,
        origin: Offset,
        context: ParticleContext = ParticleContext(),
        block: ParticleLayerBuilder.() -> Unit
    ) {
        val layerBuilder = ParticleLayerBuilder(name, origin, context).apply(block)
        layers.add(layerBuilder.build())
    }
}

/**
 * Immutable parameters for a layer.
 */
class ParticleLayerConfig(
    val origin: Offset,
    val count: Int,
    /**
     * The core logical duration of the particle effect.
     * Used as the denominator for progress calculation and the time window
     * for active interpolation (including overshot/rebound effects).
     */
    val duration: Float,
    /**
     * Extra survival time after [duration] has elapsed.
     * This buffer prevents premature disposal, allowing interpolation rebounds
     * to settle or shaders to finish long-tail fades while the alpha is already zero.
     */
    val decayPadding: Float,
    /**
     * The visual material (Shader) of the particles.
     * * NOTE: If a material is provided, it will take full control of the particle's
     * fragment output, effectively OVERRIDING the [ParticleLayer.color] node.
     * For color manipulation in this mode, use the material's internal shader logic.
     */
    val material: Material?
)

@ParticleDslMarker
class ParticleLayerConfigBuilder(
    private val origin: Offset,
    val context: ParticleContext
) {
    var count: Int = 1
    var duration: Float = 1.0f
    var decayPadding: Float = 0.0f
    var material: Material? = null

    internal fun build(): ParticleLayerConfig {
        return ParticleLayerConfig(origin, count, duration, decayPadding, material)
    }
}

@ParticleDslMarker
class ParticleLayerBuilder(
    val name: String,
    origin: Offset,
    val context: ParticleContext
): ParticleNodeProvider {
    @PublishedApi
    internal val configBuilder = ParticleLayerConfigBuilder(origin, context)

    inline fun config(block: ParticleLayerConfigBuilder.() -> Unit) {
        configBuilder.apply(block)
    }

    // Physics nodes (CPU initially, GPU evolution)
    var position: ParticleNode = vec2(0f, 0f)

    // Aesthetic nodes (GPU driven)
    var size: ParticleNode = scalar(1f)

    var color: ParticleNode = color(0xFFFFFFFF)

    override val math: ParticleNodeMath = ParticleNodeMath(this)
    override val env: ParticleNodeEnvironment = ParticleNodeEnvironment()
    override val ops: ParticleNodeOperations = ParticleNodeOperations()

    internal fun build(): ParticleLayer {
        return ParticleLayer(
            name = name,
            context = context,
            config = configBuilder.build(),
            position = position,
            size = size,
            color = color
        )
    }
}

/**
 * Represents a single layer's configuration.
 * Contains the nodes that will be resolved by the Pattern and Shader parsers.
 */
class ParticleLayer(
    val name: String,
    val context: ParticleContext,

    val config: ParticleLayerConfig,

    val position: ParticleNode,
    val size: ParticleNode,
    val color: ParticleNode
)

/** Entry point for the multi-layer DSL. */
fun particles(block: ParticleNodeScope.() -> Unit): ParticleNodeScope {
    return ParticleNodeScope().apply(block)
}