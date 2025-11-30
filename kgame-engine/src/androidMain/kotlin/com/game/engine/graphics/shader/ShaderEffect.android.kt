package com.game.engine.graphics.shader

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

internal class AndroidFallbackShaderEffect : ShaderEffect {

    override val supported: Boolean = false
    override var ready: Boolean = false

    override fun build(): Brush {
        return Brush.horizontalGradient(listOf(Color.White, Color.White))
    }

}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AndroidShaderEffect(shader: Shader) : ShaderEffect {
    private val compositeRuntimeEffect = RuntimeShader(shader.sksl)

    override val supported: Boolean = true
    override var ready: Boolean = false

    override fun uniform(name: String, value: Int) {
        compositeRuntimeEffect.setIntUniform(name, value)
    }

    override fun uniform(name: String, value1: Int, value2: Int) {
        compositeRuntimeEffect.setIntUniform(name, value1, value2)
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int) {
        compositeRuntimeEffect.setIntUniform(name, value1, value2, value3)
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {
        compositeRuntimeEffect.setIntUniform(name, value1, value2, value3, value4)
    }

    override fun uniform(name: String, value: Float) {
        compositeRuntimeEffect.setFloatUniform(name, value)
    }

    override fun uniform(name: String, value1: Float, value2: Float) {
        compositeRuntimeEffect.setFloatUniform(name, value1, value2)
    }

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float) {
        compositeRuntimeEffect.setFloatUniform(name, value1, value2, value3)
    }

    override fun uniform(name: String, values: FloatArray) {
        compositeRuntimeEffect.setFloatUniform(name, values)
    }

    override fun update(shader: Shader, time: Float, size: Size) {
        if (size.isEmpty()) return
        shader.applyUniforms(this, time, size.width, size.height)
        ready = true
    }

    override fun build(): Brush {
        return ShaderBrush(compositeRuntimeEffect)
    }
}

actual fun ShaderEffect(shader: Shader): ShaderEffect {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AndroidShaderEffect(shader)
    } else {
        AndroidFallbackShaderEffect()
    }
}