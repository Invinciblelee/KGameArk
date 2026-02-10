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
        val duration: Float
    ) {
        val elapsedTime: Float
            get() = when(effect) {
                is VertexEffect -> effect.elapsedTime
                is ShaderEffect -> effect.elapsedTime
                else -> 0f
            }
    }

    fun emit(useGpu: Boolean = true, block: ParticleNodeScope.() -> Unit) {
        fun duration(scope: ParticleNodeScope): Float {
            return scope.layers.maxOf { it.duration }
        }

        if (useGpu) {
            if (shaderEffects.size >= capacity) return

            val scope = ParticleNodeScope().apply(block)
            val shader = ParticleShaderParser.translate(scope)
            val effect = ShaderEffect(shader)
            if (effect.supported) {
                shaderEffects.add(EffectWrapper(effect, duration(scope)))
                return
            }
        }

        if (vertexEffects.size < capacity) {
            val scope = ParticleNodeScope().apply(block)
            val pattern = ParticlePatternParser.translate(scope)
            val effect = VertexEffect(pattern)
            vertexEffects.add(EffectWrapper(effect, duration = duration(scope)))
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
            if (wrapper.elapsedTime >= wrapper.duration + 0.5f) {
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
            if (wrapper.elapsedTime >= wrapper.duration + 0.5f) {
                vertexEffects.removeAt(i)
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