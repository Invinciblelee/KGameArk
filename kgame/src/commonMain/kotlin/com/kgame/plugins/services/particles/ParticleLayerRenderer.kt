@file:OptIn(ExperimentalMaterialVisuals::class)

package com.kgame.plugins.services.particles

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.kgame.engine.graphics.drawscope.drawVertices
import com.kgame.engine.graphics.material.ExperimentalMaterialVisuals
import com.kgame.engine.graphics.material.MaterialEffect

class ParticleLayerRenderer(
    val pattern: VertexPattern,
    val config: ParticleLayerConfig
) {

    private val effect = config.material?.let { MaterialEffect(it) }

    private var elapsedTime: Float = 0f

    val isDead: Boolean get() = elapsedTime > config.duration + config.decayPadding

    /**
     * Updates particle state.
     * Uses the first vertex of each quad as the physics anchor.
     */
    fun update(dt: Float) {
        elapsedTime += dt

        if (isDead) return

        pattern.update(dt)
        effect?.update(dt)
    }

    /**
     * Renders particle.
     */
    fun render(drawScope: DrawScope, paint: Paint) {
        if (isDead) return

        if (effect != null) {
            effect.applyTo(drawScope.size, paint)
        } else {
            paint.shader = null
        }

        drawScope.withTransform({
            translate(config.origin.x, config.origin.y)
        }) {
            drawVertices(
                vertexMode = VertexMode.Triangles,
                positions = pattern.positions,
                colors = pattern.colors,
                texCoords = pattern.texCoords,
                indices = pattern.indices,
                blendMode = if (effect == null) BlendMode.Dst else BlendMode.Src,
                paint = paint
            )
        }
    }

}

