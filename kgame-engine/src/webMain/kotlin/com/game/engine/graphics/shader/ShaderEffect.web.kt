package com.game.engine.graphics.shader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

private class WebShaderEffect(shader: Shader) : ShaderEffect {
    private val compositeRuntimeEffect = RuntimeEffect.makeForShader(shader.sksl)
    private val compositeShaderBuilder = RuntimeShaderBuilder(compositeRuntimeEffect)

    override val supported: Boolean = true
    override var ready: Boolean = false

    override fun uniform(name: String, value: Int) {
        compositeShaderBuilder.uniform(name, value)
    }

    override fun uniform(name: String, value1: Int, value2: Int) {
        compositeShaderBuilder.uniform(name, value1, value2)
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int) {
        compositeShaderBuilder.uniform(name, value1, value2, value3)
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {
        compositeShaderBuilder.uniform(name, value1, value2, value3, value4)
    }

    override fun uniform(name: String, value: Float) {
        compositeShaderBuilder.uniform(name, value)
    }

    override fun uniform(name: String, value1: Float, value2: Float) {
        compositeShaderBuilder.uniform(name, value1, value2)
    }

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float) {
        compositeShaderBuilder.uniform(name, value1, value2, value3)
    }

    override fun uniform(name: String, values: FloatArray) {
        compositeShaderBuilder.uniform(name, values)
    }

    override fun update(shader: Shader, time: Float, size: Size) {
        if (size.isEmpty()) return
        shader.applyUniforms(this, time, size.width, size.height)
        ready = true
    }

    override fun build(): Brush {
        return ShaderBrush(compositeShaderBuilder.makeShader())
    }
}

actual fun ShaderEffect(shader: Shader): ShaderEffect {
    return WebShaderEffect(shader)
}