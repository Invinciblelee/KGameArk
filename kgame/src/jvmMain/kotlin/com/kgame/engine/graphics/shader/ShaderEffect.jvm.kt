package com.kgame.engine.graphics.shader

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

private class JvmShaderEffect(override val shader: Shader) : ShaderEffect() {
    private val runtimeEffect = RuntimeEffect.makeForShader(shader.sksl)
    private val runtimeShaderBuilder = RuntimeShaderBuilder(runtimeEffect)

    override fun uniform(name: String, value: Int) {
        runtimeShaderBuilder.uniform(name, value)
        markDirty()
    }

    override fun uniform(name: String, value1: Int, value2: Int) {
        runtimeShaderBuilder.uniform(name, value1, value2)
        markDirty()
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int) {
        runtimeShaderBuilder.uniform(name, value1, value2, value3)
        markDirty()
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {
        runtimeShaderBuilder.uniform(name, value1, value2, value3, value4)
        markDirty()
    }

    override fun uniform(name: String, value: Float) {
        runtimeShaderBuilder.uniform(name, value)
        markDirty()
    }

    override fun uniform(name: String, value1: Float, value2: Float) {
        runtimeShaderBuilder.uniform(name, value1, value2)
        markDirty()
    }

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float) {
        runtimeShaderBuilder.uniform(name, value1, value2, value3)
        markDirty()
    }

    override fun uniform(name: String, values: FloatArray) {
        runtimeShaderBuilder.uniform(name, values)
        markDirty()
    }

    override fun createBrush(): Brush {
        return ShaderBrush(runtimeShaderBuilder.makeShader())
    }
}

actual fun ShaderEffect(shader: Shader): ShaderEffect {
    return JvmShaderEffect(shader)
}