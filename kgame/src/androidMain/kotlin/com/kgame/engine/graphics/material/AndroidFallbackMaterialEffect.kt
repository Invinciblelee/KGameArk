package com.kgame.engine.graphics.material

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.SolidColor

internal object AndroidFallbackMaterialEffect : MaterialEffect() {

    private val DefaultBrush = SolidColor(Color.White)

    override val material: Material = object : Material {
        override val sksl: String = ""
    }

    override val support: Boolean = false

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

    override fun createBrush(): Brush = DefaultBrush

}