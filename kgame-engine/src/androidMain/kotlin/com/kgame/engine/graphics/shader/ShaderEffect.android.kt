package com.kgame.engine.graphics.shader

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

internal class AndroidFallbackShaderEffect : ShaderEffect {
    companion object {
       val FallbackBrush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }

    override val supported: Boolean = false
    override var ready: Boolean = false

    override fun obtain(): Brush {
        return FallbackBrush
    }

}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AndroidShaderEffect(val shader: Shader) : ShaderEffect {
    private val compositeRuntimeEffect = RuntimeShader(shader.sksl)

    private var brush: ShaderBrush? = null
    private var size: Size = Size.Unspecified

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

    override fun updateResolution(size: Size) {
        if (this.size == size || size.isEmpty()) return
        this.size = size
        super.updateResolution(size)
        ready = true
    }

    override fun obtain(): Brush {
        with(shader) { applyUniforms() }
        return brush ?: ShaderBrush(compositeRuntimeEffect).also { brush = it }
    }

}

actual fun ShaderEffect(shader: Shader): ShaderEffect {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AndroidShaderEffect(shader)
    } else {
        AndroidFallbackShaderEffect()
    }
}