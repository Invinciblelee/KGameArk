package com.game.engine.graphics.shader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.game.engine.graphics.shader.Shader.Companion.RESOLUTION
import com.game.engine.graphics.shader.Shader.Companion.TIME

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

    /** Sets a color uniform for this shader */
    fun uniform(name: String, value: Color) {
        uniform(name, value.red, value.green, value.blue)
    }

    /** Sets colors uniform for this shader */
    fun uniform(name: String, values: Array<Color>) {
        uniform(name, values.flatMap { listOf(it.red, it.green, it.blue) }.toFloatArray())
    }

    /** Updates the resolution uniform.*/
    fun updateResolution(size: Size) {
        if (size.isEmpty()) return
        uniform(RESOLUTION, size.width, size.height, size.width / size.height)
    }

    /** Updates the time uniform. */
    fun updateTime(time: Float) {
        uniform(TIME, time)
    }

    /** Obtains an updates ShaderBrush*/
    fun obtain(): Brush
}

expect fun ShaderEffect(shader: Shader): ShaderEffect