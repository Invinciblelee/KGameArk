package com.kgame.engine.graphics.material

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush

abstract class MaterialEffect {

    protected abstract val material: Material

    open val support: Boolean get() = true

    private var brush: Brush? = null
    private var isDirty: Boolean = true
    private var isInitialized: Boolean = false

    private val colorBuffer = mutableMapOf<String, FloatArray>()

    private var size: Size = Size.Unspecified
    private val matrix: Matrix = Matrix()

    var elapsedTime: Float = 0f
        private set

    protected fun markDirty() {
        isDirty = true
    }

    abstract fun input(name: String, shader: Shader)

    abstract fun input(name: String, colorFilter: ColorFilter)

    abstract fun uniform(name: String, value: Int)

    abstract fun uniform(name: String, value1: Int, value2: Int)

    abstract fun uniform(name: String, value1: Int, value2: Int, value3: Int)

    abstract fun uniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int)

    abstract fun uniform(name: String, value: Float)

    abstract fun uniform(name: String, value1: Float, value2: Float)

    abstract fun uniform(name: String, value1: Float, value2: Float, value3: Float)

    abstract fun uniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float)

    abstract fun uniform(name: String, values: FloatArray)

    /** Sets a color uniform for this shader (RGBA) */
    fun uniform(name: String, value: Color) {
        // Standardizing to RGBA for shader compatibility
        uniform(name, value.red, value.green, value.blue, value.alpha)
    }

    /** Sets an array of color uniforms for this shader (RGBA) */
    fun uniform(name: String, values: Array<Color>) {
        // Each color now occupies 4 slots (R, G, B, A)
        val stride = 4
        val requiredSize = values.size * stride

        val buffer = colorBuffer.getOrPut(name) {
            FloatArray(requiredSize)
        }

        val finalBuffer = if (buffer.size == requiredSize) {
            buffer
        } else {
            val newBuffer = FloatArray(requiredSize)
            colorBuffer[name] = newBuffer
            newBuffer
        }

        var i = 0
        while (i < values.size) {
            val color = values[i]
            val offset = i * stride
            finalBuffer[offset] = color.red
            finalBuffer[offset + 1] = color.green
            finalBuffer[offset + 2] = color.blue
            finalBuffer[offset + 3] = color.alpha
            i++
        }

        uniform(name, finalBuffer)
    }

    /**
     * Updates the shader with the given delta time
     */
    fun update(deltaTime: Float) {
        this.elapsedTime += deltaTime

        if (!isInitialized) {
            with(material) { onSetup() }
            isInitialized = true
        }

        with(material) { onUpdate() }
    }


    protected abstract fun createBrush(): Brush

    /** Obtains an updates ShaderBrush*/
    fun obtainBrush(): Brush {
        if (isDirty || brush == null) {
            brush = createBrush()
            isDirty = false
        }
        return brush!!
    }

    fun obtainBrush(size: Size): Brush {
        val dirty = isDirty
        val brush = obtainBrush()

        val sizeChanged = this.size != size
        if (sizeChanged || dirty) {
            if (sizeChanged) {
                this.size = size
                matrix.reset()
                if (!size.isEmpty()) {
                    matrix.scale(size.width, size.height)
                }
            }
            if (brush is ShaderBrush) {
                brush.transform = if (size.isEmpty()) {
                    null
                } else {
                    matrix
                }
            }
        }
        return brush
    }

    fun applyTo(size: Size, paint: Paint, alpha: Float = 1f) {
        obtainBrush().applyTo(size, paint, alpha)
    }
}

expect fun MaterialEffect(material: Material): MaterialEffect