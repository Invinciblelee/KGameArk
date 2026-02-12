package com.kgame.engine.graphics.material

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import com.kgame.engine.graphics.material.Material.Companion.DELTA_TIME
import com.kgame.engine.graphics.material.Material.Companion.RESOLUTION
import com.kgame.engine.graphics.material.Material.Companion.TIME

/**
 * Describes a platform-independent shader effect
 */
abstract class MaterialEffect {

    protected abstract val material: Material

    /** Indicates if the current platform is supported*/
    open val supported: Boolean = true

    /** Defines if the effect is ready to be displayed */
    val ready: Boolean get() = !size.isEmpty()

    private var brush: Brush? = null
    private var isDirty: Boolean = true

    private val useTime by lazy { material.sksl.contains(TIME) }
    private val useDeltaTime by lazy { material.sksl.contains(DELTA_TIME) }
    private val useResolution by lazy { material.sksl.contains(RESOLUTION) }

    var elapsedTime: Float = 0f
        private set

    var size: Size = Size.Unspecified
        private set

    private val colorBufferCache = mutableMapOf<String, FloatArray>()

    protected fun markDirty() {
        isDirty = true
    }

    abstract fun input(name: String, shader: Shader)

    abstract fun input(name: String, colorFilter: ColorFilter)

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
    fun setResolution(size: Size) {
        if (!useResolution || this.size == size || size.isEmpty()) return
        this.size = size
        uniform(RESOLUTION, size.width, size.height)
    }

    /** Updates the time uniform. */
    fun update(deltaTime: Float) {
        elapsedTime += deltaTime
        if (useTime) uniform(TIME, elapsedTime)
        if (useDeltaTime) uniform(DELTA_TIME, deltaTime)
    }

    fun applyMaterialUniforms() {
        with(material) {  applyUniforms()  }
    }

    protected abstract fun createBrush(): Brush

    /** Obtains an updates ShaderBrush*/
    fun obtainBrush(): Brush {
        with(material) { applyUniforms() }
        if (isDirty || brush == null) {
            brush = createBrush()
            isDirty = false
        }
        return brush!!
    }

    fun applyTo(size: Size, paint: Paint, alpha: Float = 1f) {
        obtainBrush().applyTo(size, paint, alpha)
    }
}

expect fun MaterialEffect(material: Material): MaterialEffect