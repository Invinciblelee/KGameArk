package com.kgame.plugins.services.particles

import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope

class ParticleService(val capacity: Int = 64) {
    private val particleRenderers = ArrayList<List<ParticleLayerRenderer>>(capacity)
    private val paint = Paint()

    fun emit(block: ParticleNodeScope.() -> Unit) {
        if (particleRenderers.size >= capacity) {
            return
        }

        val scope = particles(block)
        val renderers = ParticleVertexParser.translate(scope)
        particleRenderers.add(renderers)
    }

    internal fun update(dt: Float) {
        var i = particleRenderers.size - 1
        while (i >= 0) {
            val renderGroup = particleRenderers[i]
            var groupAlive = false

            var j = 0
            while (j < renderGroup.size) {
                val renderer = renderGroup[j++]
                renderer.update(dt)
                if (!renderer.isDead) {
                    groupAlive = true
                }
            }

            if (!groupAlive) {
                particleRenderers.removeAt(i)
            }

            i--
        }
    }

    internal fun render(scope: DrawScope) {
        val groupSize = particleRenderers.size
        if (groupSize == 0) return

        var i = 0
        while (i < groupSize) {
            val renderGroup = particleRenderers[i++]
            val layerCount = renderGroup.size

            var j = 0
            while (j < layerCount) {
                val renderer = renderGroup[j++]
                renderer.render(scope, paint)
            }
        }
    }
}
