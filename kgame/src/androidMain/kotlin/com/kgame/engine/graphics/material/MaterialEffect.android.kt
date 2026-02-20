package com.kgame.engine.graphics.material

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap

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

private class AndroidOpenGLMaterialEffect(override val material: Material) : MaterialEffect() {

    override val supported: Boolean = true

    // --- State Storage (Strictly thread-safe for GL sync) ---
    private val floatUniforms = mutableMapOf<String, FloatArray>()
    private val intUniforms = mutableMapOf<String, IntArray>()
    private val inputShaders = mutableMapOf<String, Shader>()

    // --- GPU & Bitmap Resources ---
    private var persistentBitmap: Bitmap? = null
    private var pixelBuffer: java.nio.ByteBuffer? = null
    private var cachedBrush: Brush? = null

    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface = EGL14.EGL_NO_SURFACE
    private var programId = -1

    private val resolution = 512

    // --- Overrides (Strictly matched with your base class parameter names) ---

    override fun input(name: String, shader: Shader) {
        inputShaders[name] = shader
    }

    override fun input(name: String, colorFilter: ColorFilter) {
        // Implementation for ColorFilter would involve dynamic fragment shader injection
    }

    override fun uniform(name: String, value: Int) {
        intUniforms[name] = intArrayOf(value)
    }

    override fun uniform(name: String, value1: Int, value2: Int) {
        intUniforms[name] = intArrayOf(value1, value2)
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int) {
        intUniforms[name] = intArrayOf(value1, value2, value3)
    }

    override fun uniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {
        intUniforms[name] = intArrayOf(value1, value2, value3, value4)
    }

    override fun uniform(name: String, value: Float) {
        floatUniforms[name] = floatArrayOf(value)
    }

    override fun uniform(name: String, value1: Float, value2: Float) {
        floatUniforms[name] = floatArrayOf(value1, value2)
    }

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float) {
        floatUniforms[name] = floatArrayOf(value1, value2, value3)
    }

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
        floatUniforms[name] = floatArrayOf(value1, value2, value3, value4)
    }

    override fun uniform(name: String, values: FloatArray) {
        floatUniforms[name] = values
    }

    // --- Core Bridge Logic ---

    override fun createBrush(): Brush {
        if (persistentBitmap == null) {
            setupOpenGLContext()
        }

        // Run the simulation: Shader + Uniforms -> GPU -> Bitmap
        performOffscreenRender()

        return cachedBrush ?: ShaderBrush(ImageShader(persistentBitmap!!.asImageBitmap())).also {
            cachedBrush = it
        }
    }

    private fun setupOpenGLContext() {
        // Initialize EGL Display
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        // Configure EGL for Pbuffer (Off-screen)
        val configAttrs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, configAttrs, 0, configs, 0, 1, numConfigs, 0)

        val contextAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttrs, 0)

        val pbufferAttrs = intArrayOf(EGL14.EGL_WIDTH, resolution, EGL14.EGL_HEIGHT, resolution, EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], pbufferAttrs, 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        // Setup persistent bitmap resources
        persistentBitmap = createBitmap(resolution, resolution)
        pixelBuffer = java.nio.ByteBuffer.allocateDirect(resolution * resolution * 4).apply {
            order(java.nio.ByteOrder.nativeOrder())
        }

        // Simple Shader Compilation (Translating AGSL-like code to GLES 2.0)
        val vs = "attribute vec4 p; void main(){ gl_Position=p; }"
        val fsHeader = "precision mediump float;\n"
        val fsBody = material.sksl.replace("main(vec2 uv)", "main()")
        val fsFooter = "\nvoid main(){ main(); }"

        val vShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).apply {
            GLES20.glShaderSource(this, vs); GLES20.glCompileShader(this)
        }
        val fShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).apply {
            GLES20.glShaderSource(this, fsHeader + fsBody + fsFooter); GLES20.glCompileShader(this)
        }
        programId = GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vShader); GLES20.glAttachShader(this, fShader)
            GLES20.glLinkProgram(this)
        }
    }

    private fun performOffscreenRender() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return

        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        GLES20.glUseProgram(programId)
        GLES20.glViewport(0, 0, resolution, resolution)

        // Bind all collected float uniforms
        floatUniforms.forEach { (name, values) ->
            val loc = GLES20.glGetUniformLocation(programId, name)
            if (loc >= 0) when (values.size) {
                1 -> GLES20.glUniform1f(loc, values[0])
                2 -> GLES20.glUniform2f(loc, values[0], values[1])
                3 -> GLES20.glUniform3f(loc, values[0], values[1], values[2])
                4 -> GLES20.glUniform4f(loc, values[0], values[1], values[2], values[3])
            }
        }

        // Bind all collected int uniforms
        intUniforms.forEach { (name, values) ->
            val loc = GLES20.glGetUniformLocation(programId, name)
            if (loc >= 0) when (values.size) {
                1 -> GLES20.glUniform1i(loc, values[0])
                2 -> GLES20.glUniform2i(loc, values[0], values[1])
                3 -> GLES20.glUniform3i(loc, values[0], values[1], values[2])
                4 -> GLES20.glUniform4i(loc, values[0], values[1], values[2], values[3])
            }
        }

        // Execute drawing to trigger the fragment shader
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Flush GPU result to the CPU-side Bitmap
        pixelBuffer?.rewind()
        GLES20.glReadPixels(0, 0, resolution, resolution, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer)
        pixelBuffer?.rewind()
        persistentBitmap?.copyPixelsFromBuffer(pixelBuffer!!)
    }
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