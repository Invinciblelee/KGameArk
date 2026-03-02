package com.kgame.engine.graphics.material

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import com.kgame.engine.graphics.material.Material.Companion.DELTA_TIME
import com.kgame.engine.graphics.material.Material.Companion.TIME
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

abstract class MaterialEffect {

    protected abstract val material: Material

    /** Indicates if the current platform is supported*/
    open val supported: Boolean = true

    private var brush: Brush? = null
    private var isDirty: Boolean = true
    private var isInitialized: Boolean = false

    private val colorBufferCache = mutableMapOf<String, FloatArray>()

    private val useTime by lazy { material.sksl.contains(TIME) }
    private val useDeltaTime by lazy { material.sksl.contains(DELTA_TIME) }

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

    /** Updates the time uniform. */
    fun update(deltaTime: Float) {
        elapsedTime += deltaTime
        if (useTime) uniform(TIME, elapsedTime)
        if (useDeltaTime) uniform(DELTA_TIME, deltaTime)
    }

    protected abstract fun createBrush(): Brush

    /** Obtains an updates ShaderBrush*/
    fun obtainBrush(): Brush {
        if (!isInitialized) {
            with(material) { onSetup() }
            isInitialized = true
        }

        with(material) { onUpdate() }
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

private class SkiaMaterialEffect(override val material: Material) : MaterialEffect() {
    private val runtimeEffect = RuntimeEffect.makeForShader(material.sksl)
    private val runtimeShaderBuilder = RuntimeShaderBuilder(runtimeEffect)

    override fun input(name: String, shader: Shader) {
        runtimeShaderBuilder.child(name, )
        markDirty()
    }

    override fun input(name: String, colorFilter: ColorFilter) {
        runtimeShaderBuilder.child(name, colorFilter.asSkiaColorFilter())
        markDirty()
    }

    override fun uniform(name: String, value: Int) {
        runtimeShaderBuilder.uniform(name, value)
        markDirty()
    }

    override fun uniform(name: String, value1: Int, value2: Int) {
        runtimeShaderBuilder.uniform(name, value1, value2)
        markDirty()
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int) {
        runtimeShaderBuilder.uniform(name, value1, value2, value3)
        markDirty()
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {
        runtimeShaderBuilder.uniform(name, value1, value2, value3, value4)
        markDirty()
    }

    override fun uniform(name: String, value: Float) {
        runtimeShaderBuilder.uniform(name, value)
        markDirty()
    }

    override fun uniform(name: String, value1: Float, value2: Float) {
        runtimeShaderBuilder.uniform(name, value1, value2)
        markDirty()
    }

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float) {
        runtimeShaderBuilder.uniform(name, value1, value2, value3)
        markDirty()
    }

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
        runtimeShaderBuilder.uniform(name, value1, value2, value3, value4)
        markDirty()
    }

    override fun uniform(name: String, values: FloatArray) {
        runtimeShaderBuilder.uniform(name, values)
        markDirty()
    }

    override fun createBrush(): Brush {
        return ShaderBrush(runtimeShaderBuilder.makeShader())
    }
}

expect fun MaterialEffect(material: Material): MaterialEffect