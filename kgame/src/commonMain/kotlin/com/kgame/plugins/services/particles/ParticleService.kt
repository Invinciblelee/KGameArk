package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import com.kgame.engine.graphics.shader.ShaderEffect

class ParticleService(val capacity: Int = 64) {
    private val vertexEffects = ArrayList<EffectWrapper<VertexEffect>>(capacity)
    private val shaderEffects = ArrayList<EffectWrapper<ShaderEffect>>(capacity)
    private val paint = Paint().apply { isAntiAlias = false }

    fun emit(
        useGpu: Boolean = true,
        context: ParticleContext = ParticleContext.Default,
        block: ParticleNodeScope.() -> Unit
    ) {
        val scope = particles(context, block)

        // Try GPU first if requested
        if (useGpu && shaderEffects.size < capacity) {
            val effects = ParticleShaderParser.translate(scope)
            if (effects.all { it.supported }) {
                shaderEffects.add(EffectWrapper(effects, scope))
                return
            }
        }

        // Fallback to CPU
        if (vertexEffects.size < capacity) {
            val effects = ParticleVertexParser.translate(scope)
            vertexEffects.add(EffectWrapper(effects, scope))
        }
    }

    internal fun update(dt: Float) {
        updateInternal(vertexEffects, dt)
        updateInternal(shaderEffects, dt)
    }

    private fun <T> updateInternal(list: ArrayList<EffectWrapper<T>>, dt: Float) {
        var i = list.size - 1
        while (i >= 0) {
            val wrapper = list[i]
            wrapper.update(dt)
            if (wrapper.isDead) list.removeAt(i)
            i--
        }
    }

    internal fun render(scope: DrawScope) {
        // Render all layers in each wrapper
        var i = 0
        while (i < vertexEffects.size) {
            vertexEffects[i].render(scope, paint)
            i++
        }

        var j = 0
        while (j < shaderEffects.size) {
            shaderEffects[j].render(scope, paint)
            j++
        }
    }
}

private class EffectWrapper<T>(
    val effects: List<T>,
    val scope: ParticleNodeScope
) {
    private val duration: Float = scope.layers.maxOf { it.duration }
    private var elapsedTime: Float = 0f

    val isDead: Boolean get() = elapsedTime >= duration

    fun update(dt: Float) {
        elapsedTime += dt
        val size = effects.size
        var i = 0
        while (i < size) {
            val effect = effects[i]
            // Direct type check is fast enough for localized updates
            if (effect is VertexEffect) effect.update(dt)
            else if (effect is ShaderEffect) effect.update(dt)
            i++
        }
    }

    fun render(scope: DrawScope, paint: Paint) {
        val layers = this.scope.layers
        val size = effects.size
        var i = 0

        while (i < size) {
            val layer = layers[i]
            val effect = effects[i]

            if (elapsedTime > layer.duration) {
                i++
                continue
            }

            val rect = layer.frame
            val size = rect.size
            val center = rect.center

            scope.withTransform({
                translate(center.x, center.y)
            }) {
                if (effect is VertexEffect) {
                    drawIntoCanvas { canvas ->
                        canvas.drawVertices(effect.obtain(), BlendMode.Dst, paint)
                    }
                } else if (effect is ShaderEffect) {
                    drawRect(
                        brush = effect.obtain(),
                        topLeft = Offset(-size.width / 2f, -size.height / 2f),
                        size = rect.size
                    )
                }
            }
            i++
        }
    }
}