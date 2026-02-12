package com.kgame.plugins.services.particles

import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kgame.engine.log.Logger
import kotlin.time.Clock

class ParticleService(val capacity: Int = 64) {
    private val particleRenderers = ArrayList<List<ParticleRenderer>>(capacity)
    private val paint = Paint().apply {
        isAntiAlias = false
    }

    fun emit(
        context: ParticleContext = ParticleContext.Default,
        block: ParticleNodeScope.() -> Unit
    ) {
        if (particleRenderers.size >= capacity) {
            return
        }

        val scope = particles(context, block)
        val renderers = ParticleVertexParser.translate(scope)
        particleRenderers.add(renderers)
    }

    internal fun update(dt: Float) {
        val start = Clock.System.now()

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

        Logger.debug("ParticleService", "Update took ${Clock.System.now() - start}")
    }

    internal fun render(scope: DrawScope) {
        val groupSize = particleRenderers.size
        if (groupSize == 0) return

        val start = Clock.System.now()

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

        Logger.debug("ParticleService", "Render took ${Clock.System.now() - start}")
    }
}
