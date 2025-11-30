package com.game.engine.graphics.shader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush

/**
 * Describes a platform-independent shader effect
 */
interface ShaderEffect {

    /** Indicates if the current platform is supported*/
    val supported: Boolean

    /** Defines if the effect is ready to be displayed */
    val ready: Boolean

    /** Sets a int array uniform for this shader */
    fun uniform(name: String, value: Int) = Unit

    /** Sets a int array uniform for this shader */
    fun uniform(name: String, value1: Int, value2: Int) = Unit

    /** Sets a int array uniform for this shader */
    fun uniform(name: String, value1: Int, value2: Int, value3: Int) = Unit

    /** Sets a int array uniform for this shader */
    fun uniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) = Unit

    /** Sets a float array uniform for this shader */
    fun uniform(name: String, value: Float) = Unit

    /** Sets a float array uniform for this shader */
    fun uniform(name: String, value1: Float, value2: Float) = Unit

    /** Sets a float array uniform for this shader */
    fun uniform(name: String, value1: Float, value2: Float, value3: Float) = Unit

    /** Sets a float array uniform for this shader */
    fun uniform(name: String, values: FloatArray) = Unit

    /** Updates the uniforms for the shader, on changes of the size or time.*/
    fun update(shader: Shader, time: Float, size: Size) {}

    /** Builds an updates ShaderBrush*/
    fun build(): Brush
}

expect fun ShaderEffect(shader: Shader): ShaderEffect