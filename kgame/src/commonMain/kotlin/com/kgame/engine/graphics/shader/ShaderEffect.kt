package com.kgame.engine.graphics.shader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kgame.engine.graphics.shader.Shader.Companion.DELTA_TIME
import com.kgame.engine.graphics.shader.Shader.Companion.RESOLUTION
import com.kgame.engine.graphics.shader.Shader.Companion.TIME

/**
 * Describes a platform-independent shader effect
 */
abstract class ShaderEffect {

    protected abstract val shader: Shader

    /** Indicates if the current platform is supported*/
    open val supported: Boolean = true

    /** Defines if the effect is ready to be displayed */
    val ready: Boolean get() = !size.isEmpty()

    private var brush: Brush? = null
    private var isDirty: Boolean = true
    private var size: Size = Size.Unspecified

    private var totalTime: Float = 0f

    private val colorBufferCache = mutableMapOf<String, FloatArray>()

    protected fun markDirty() {
        isDirty = true
    }

    /** Sets a int array uniform for this shader */
    abstract fun uniform(name: String, value: Int)

    /** Sets a int array uniform for this shader */
    abstract fun uniform(name: String, value1: Int, value2: Int)

    /** Sets a int array uniform for this shader */
    abstract fun uniform(name: String, value1: Int, value2: Int, value3: Int)

    /** Sets a int array uniform for this shader */
    abstract fun uniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int)

    /** Sets a float array uniform for this shader */
    abstract fun uniform(name: String, value: Float)

    /** Sets a float array uniform for this shader */
    abstract fun uniform(name: String, value1: Float, value2: Float)

    /** Sets a float array uniform for this shader */
    abstract fun uniform(name: String, value1: Float, value2: Float, value3: Float)

    /** Sets a float array uniform for this shader */
    abstract fun uniform(name: String, values: FloatArray)

    /** Sets a color uniform for this shader */
    fun uniform(name: String, value: Color) {
        uniform(name, value.red, value.green, value.blue)
    }

    /** Sets colors uniform for this shader */
    fun uniform(name: String, values: Array<Color>) {
        val requiredSize = values.size * 3

        val buffer = colorBufferCache.getOrPut(name) {
            FloatArray(requiredSize)
        }

        val finalBuffer = if (buffer.size == requiredSize) {
            buffer
        } else {
            val newBuffer = FloatArray(requiredSize)
            colorBufferCache[name] = newBuffer
            newBuffer
        }

        var index = 0
        while (index < values.size) {
            val color = values[index++]
            finalBuffer[index * 3] = color.red
            finalBuffer[index * 3 + 1] = color.green
            finalBuffer[index * 3 + 2] = color.blue
        }

        uniform(name, finalBuffer)
    }

    /** Updates the resolution uniform.*/
    fun updateResolution(size: Size) {
        if (this.size == size || size.isEmpty()) return
        this.size = size
        uniform(RESOLUTION, size.width, size.height, size.width / size.height)
    }

    /** Updates the time uniform. */
    fun updateTime(deltaTime: Float) {
        totalTime += deltaTime
        uniform(TIME, totalTime)
        if (shader.sksl.contains(DELTA_TIME)) {
            uniform(DELTA_TIME, deltaTime)
        }
    }

    protected abstract fun createBrush(): Brush

    /** Obtains an updates ShaderBrush*/
    fun obtain(): Brush {
        with(shader) { applyUniforms() }
        if (isDirty || brush == null) {
            brush = createBrush()
            isDirty = false
        }
        return brush!!
    }
}

expect fun ShaderEffect(shader: Shader): ShaderEffect