package com.kgame.engine.graphics.shader

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

private object AndroidFallbackShaderEffect : ShaderEffect() {
    private val FallbackBrush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))

    override val shader: Shader = object : Shader {
        override val sksl: String = ""
    }

    override val supported: Boolean = false

    override fun uniform(name: String, value: Int) = Unit

    override fun uniform(name: String, value1: Int, value2: Int) = Unit

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int) = Unit

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) = Unit

    override fun uniform(name: String, value: Float) = Unit

    override fun uniform(name: String, value1: Float, value2: Float) = Unit

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float) = Unit

    override fun uniform(name: String, values: FloatArray) = Unit

    override fun createBrush(): Brush = FallbackBrush

}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AndroidShaderEffect(override val shader: Shader) : ShaderEffect() {
    private val runtimeShader = RuntimeShader(shader.sksl)

    override val supported: Boolean = true

    override fun uniform(name: String, value: Int) {
        runtimeShader.setIntUniform(name, value)
    }

    override fun uniform(name: String, value1: Int, value2: Int) {
        runtimeShader.setIntUniform(name, value1, value2)
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int) {
        runtimeShader.setIntUniform(name, value1, value2, value3)
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {
        runtimeShader.setIntUniform(name, value1, value2, value3, value4)
    }

    override fun uniform(name: String, value: Float) {
        runtimeShader.setFloatUniform(name, value)
    }

    override fun uniform(name: String, value1: Float, value2: Float) {
        runtimeShader.setFloatUniform(name, value1, value2)
    }

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float) {
        runtimeShader.setFloatUniform(name, value1, value2, value3)
    }

    override fun uniform(name: String, values: FloatArray) {
        runtimeShader.setFloatUniform(name, values)
    }

    override fun createBrush(): Brush {
        return ShaderBrush(runtimeShader)
    }

}

actual fun ShaderEffect(shader: Shader): ShaderEffect {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AndroidShaderEffect(shader)
    } else {
        AndroidFallbackShaderEffect
    }
}