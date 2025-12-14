package com.kgame.engine.graphics.shader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

private class IosShaderEffect(val shader: Shader) : ShaderEffect {
    private val compositeRuntimeEffect = RuntimeEffect.makeForShader(shader.sksl)
    private val compositeShaderBuilder = RuntimeShaderBuilder(compositeRuntimeEffect)

    private var brush: ShaderBrush? = null
    private var size: Size = Size.Unspecified
    private var isDirty: Boolean = true

    override val supported: Boolean = true
    override var ready: Boolean = false

    override fun uniform(name: String, value: Int) {
        compositeShaderBuilder.uniform(name, value)
        isDirty = true
    }

    override fun uniform(name: String, value1: Int, value2: Int) {
        compositeShaderBuilder.uniform(name, value1, value2)
        isDirty = true
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int) {
        compositeShaderBuilder.uniform(name, value1, value2, value3)
        isDirty = true
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {
        compositeShaderBuilder.uniform(name, value1, value2, value3, value4)
        isDirty = true
    }

    override fun uniform(name: String, value: Float) {
        compositeShaderBuilder.uniform(name, value)
        isDirty = true
    }

    override fun uniform(name: String, value1: Float, value2: Float) {
        compositeShaderBuilder.uniform(name, value1, value2)
        isDirty = true
    }

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float) {
        compositeShaderBuilder.uniform(name, value1, value2, value3)
        isDirty = true
    }

    override fun uniform(name: String, values: FloatArray) {
        compositeShaderBuilder.uniform(name, values)
        isDirty = true
    }

    override fun updateResolution(size: Size) {
        if (this.size == size || size.isEmpty()) return
        this.size = size
        super.updateResolution(size)
        ready = true
        isDirty = true
    }

    override fun obtain(): Brush {
        with(shader) { applyUniforms() }
        if (brush == null || isDirty) {
            brush = ShaderBrush(compositeShaderBuilder.makeShader())
            isDirty = false
        }
        return brush!!
    }
}

actual fun ShaderEffect(shader: Shader): ShaderEffect {
    return IosShaderEffect(shader)
}