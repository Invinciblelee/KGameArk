package com.kgame.engine.graphics.material

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidColorFilter

private object AndroidFallbackMaterialEffect : MaterialEffect() {
    private val FallbackBrush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))

    override val material: Material = object : Material {
        override val sksl: String = ""
    }

    override val supported: Boolean = false

    override fun input(name: String, shader: Shader) = Unit

    override fun input(name: String, colorFilter: ColorFilter) = Unit

    override fun uniform(name: String, value: Int) = Unit

    override fun uniform(name: String, value1: Int, value2: Int) = Unit

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int) = Unit

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) = Unit

    override fun uniform(name: String, value: Float) = Unit

    override fun uniform(name: String, value1: Float, value2: Float) = Unit

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float) = Unit

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) = Unit

    override fun uniform(name: String, values: FloatArray) = Unit

    override fun createBrush(): Brush = FallbackBrush

}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AndroidMaterialEffect(override val material: Material) : MaterialEffect() {
    companion object {
        private const val TAG = "AndroidMaterialEffect"
    }

    private val runtimeShader = RuntimeShader(material.sksl)

    override val supported: Boolean = true

    override fun input(name: String, shader: Shader) {
        runtimeShader.setInputShader(name, shader)
    }

    override fun input(name: String, colorFilter: ColorFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            runtimeShader.setInputColorFilter(name, colorFilter.asAndroidColorFilter())
        } else {
            Log.w(TAG, "ColorFilter is not supported on Android API ${Build.VERSION.SDK_INT}")
        }
    }

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

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
        runtimeShader.setFloatUniform(name, value1, value2, value3, value4)
    }

    override fun uniform(name: String, values: FloatArray) {
        runtimeShader.setFloatUniform(name, values)
    }

    override fun createBrush(): Brush {
        return ShaderBrush(runtimeShader)
    }

}

actual fun MaterialEffect(material: Material): MaterialEffect {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AndroidMaterialEffect(material)
    } else {
        AndroidFallbackMaterialEffect
    }
}