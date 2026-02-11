package com.kgame.plugins.services.particles

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.kgame.engine.graphics.shader.ShaderEffect

class ParticleService(val capacity: Int = 64) {
    private val vertexEffects = ArrayList<EffectWrapper<VertexEffect>>(capacity)
    private val paint = Paint().apply { isAntiAlias = false }
    private val shaderEffects = ArrayList<EffectWrapper<ShaderEffect>>(capacity)

    private class EffectWrapper<T>(
        val effect: T,
        val scope: ParticleNodeScope
    ) {
        val duration: Float = scope.layers.maxOf { it.duration }

        val elapsedTime: Float
            get() = when(effect) {
                is VertexEffect -> effect.elapsedTime
                is ShaderEffect -> effect.elapsedTime
                else -> 0f
            }

        val isDead: Boolean
            get() = elapsedTime >= duration + 0.5f
    }

    fun emit(useGpu: Boolean = true, block: ParticleNodeScope.() -> Unit) {
        if (useGpu) {
            if (shaderEffects.size >= capacity) return

            val scope = particles(block)
            val shader = ParticleShaderParser.translate(scope)
            val effect = ShaderEffect(shader)
            if (effect.supported) {
                shaderEffects.add(EffectWrapper(effect, scope = scope))
                return
            }
        }

        if (vertexEffects.size < capacity) {
            val scope = particles(block)
            val pattern = ParticlePatternParser.translate(scope)
            val effect = VertexEffect(pattern)
            vertexEffects.add(EffectWrapper(effect, scope = scope))
        }
    }


    internal fun update(dt: Float) {
        updateVertexes(dt)
        updateShaders(dt)
    }

    private fun updateVertexes(dt: Float) {
        var i = vertexEffects.size - 1
        while (i >= 0) {
            val wrapper = vertexEffects[i]
            wrapper.effect.update(dt)
            if (wrapper.isDead) {
                vertexEffects.removeAt(i)
            }
            i--
        }
    }

    private fun updateShaders(dt: Float) {
        var i = shaderEffects.size - 1
        while (i >= 0) {
            val wrapper = shaderEffects[i]
            wrapper.effect.update(dt)
            if (wrapper.isDead) {
                shaderEffects.removeAt(i)
            }
            i--
        }
    }

    internal fun render(scope: DrawScope) {
        renderVertexes(scope)
        renderShaders(scope)
    }

    private fun renderVertexes(scope: DrawScope) {
        scope.drawIntoCanvas { canvas ->
            var i = vertexEffects.size - 1
            while (i >= 0) {
                val wrapper = vertexEffects[i]
                wrapper.effect.setResolution(scope.size)
                canvas.drawVertices(
                    vertices = wrapper.effect.obtain(),
                    blendMode = BlendMode.Dst,
                    paint = paint
                )
                i--
            }
        }
    }

    private fun renderShaders(scope: DrawScope) {
        var i = shaderEffects.size - 1
        while (i >= 0) {
            val wrapper = shaderEffects[i]
            wrapper.effect.setResolution(scope.size)
            scope.drawRect(wrapper.effect.obtain())
            i--
        }
    }

}