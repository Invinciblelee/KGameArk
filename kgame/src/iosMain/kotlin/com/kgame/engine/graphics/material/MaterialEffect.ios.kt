package com.kgame.engine.graphics.material

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.NativeCanvas
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asSkiaColorFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

private class IosMaterialEffect(override val material: Material) : MaterialEffect() {
    private val runtimeEffect = RuntimeEffect.makeForShader(material.sksl)
    private val runtimeShaderBuilder = RuntimeShaderBuilder(runtimeEffect)

    override fun input(name: String, shader: Shader) {
        runtimeShaderBuilder.child(name, shader)
        markDirty()
    }

    override fun input(name: String, colorFilter: ColorFilter) {
        runtimeShaderBuilder.child(name, colorFilter.asSkiaColorFilter())
        markDirty()
    }

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

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
        runtimeShaderBuilder.uniform(name, value1, value2, value3, value4)
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

actual fun MaterialEffect(material: Material): MaterialEffect {
    return IosMaterialEffect(material)
}