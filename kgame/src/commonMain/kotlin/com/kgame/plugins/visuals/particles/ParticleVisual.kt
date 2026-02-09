package com.kgame.plugins.visuals.particles

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.kgame.plugins.visuals.Visual

/**
 * Professional Particle Engine. 
 * @param capacity The maximum number of concurrent effects allowed in the pool.
 */
class ParticleVisual(val capacity: Int = 64) : Visual() {

    private val pool = Array(capacity) { ActiveEffect() }
    private val activeList = ArrayList<ActiveEffect>(capacity)
    private val paint = Paint().apply { isAntiAlias = false }

    /**
     * Spawns a new effect. If the pool is full, the request is ignored to prevent allocation.
     */
    fun spawn(count: Int, pattern: ParticlePattern) {
        if (activeList.size < capacity) {
            val instance = pool[activeList.size]
            instance.reset()
            pattern.onPopulate(count, instance.bufferContext)
            activeList.add(instance)
        }
    }

    override fun DrawScope.draw() {
        drawIntoCanvas { canvas ->
            var i = activeList.size - 1
            while (i >= 0) {
                val effect = activeList[i]
                effect.update(0.016f)

                if (effect.isDead) {
                    activeList.removeAt(i)
                } else {
                   canvas.drawVertices(
                        vertices = effect.getVertices(),
                        blendMode = BlendMode.Plus,
                        paint = paint.apply { alpha = this@ParticleVisual.alpha }
                    )
                }
                i--
            }
        }
    }
}