package com.example.kgame

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import androidx.compose.ui.unit.IntSize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * A high-performance renderer that decodes raw video packets and renders them to a [GLSurfaceView]
 * using hardware acceleration.
 *
 * This class implements a zero-copy rendering path by binding [MediaCodec] output directly
 * to an OES texture. It automatically handles video resolution changes and provides
 * various scaling strategies.
 *
 * @param glView The [GLSurfaceView] context used for rendering and event queuing.
 */
class StreamRenderer(private val glView: GLSurfaceView) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    enum class ScaleType { FitCenter, CenterCrop, Stretch, Original }

    companion object {
        private const val FLOAT_SIZE = 4
        private val VERTICES = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        private val TEX_COORDS = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)

        private val VERTEX_BUFFER: FloatBuffer = ByteBuffer.allocateDirect(VERTICES.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(VERTICES).apply { position(0) }

        private val TEX_BUFFER: FloatBuffer = ByteBuffer.allocateDirect(TEX_COORDS.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(TEX_COORDS).apply { position(0) }

        private val VERTEX_SHADER_SOURCE = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """.trimIndent()

        private val FRAGMENT_SHADER_SOURCE = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """.trimIndent()
    }

    private var program = 0
    private var textureId = 0
    private var uMVPMatrixHandle = 0
    private var uSTMatrixHandle = 0

    private val mvpMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val stMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    private var decoder: MediaCodec? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var viewSize = IntSize.Zero
    private var videoSize = IntSize.Zero

    var scaleType: ScaleType = ScaleType.FitCenter
        set(value) {
            field = value
            glView.queueEvent { updateMVPMatrix() }
        }

    @Volatile
    private var isSurfaceAvailable = false
    private var isDecoderStarted = false

    /**
     * Entry point for raw packets.
     */
    fun feedPacket(data: ByteBuffer, ptsMs: Long, mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC) {
        if (!isDecoderStarted) {
            if (decoder == null) setupDecoder(mimeType)
            return
        }

        decoder?.let {
            enqueueInput(it, data, ptsMs)
            drainOutput(it)
        }
    }

    /**
     * Internal helper to queue data to the decoder.
     */
    private fun enqueueInput(codec: MediaCodec, data: ByteBuffer, ptsMs: Long) {
        try {
            val index = codec.dequeueInputBuffer(10000)
            if (index >= 0) {
                codec.getInputBuffer(index)?.let { buffer ->
                    buffer.clear()
                    buffer.put(data)
                    codec.queueInputBuffer(index, 0, data.remaining(), ptsMs * 1000, 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Decoupled output buffer draining logic.
     * Correctly handles INFO_OUTPUT_FORMAT_CHANGED outside the index range.
     */
    private fun drainOutput(codec: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        var index = codec.dequeueOutputBuffer(info, 0)

        while (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
            when (index) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> handleFormatChanged(codec.outputFormat)
                else -> {
                    if (index >= 0) {
                        codec.releaseOutputBuffer(index, true)
                    }
                }
            }
            index = codec.dequeueOutputBuffer(info, 0)
        }
    }

    private fun handleFormatChanged(format: MediaFormat) {
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)

        val cropLeft = if (format.containsKey("crop-left")) format.getInteger("crop-left") else 0
        val cropRight = if (format.containsKey("crop-right")) format.getInteger("crop-right") else width - 1
        val cropTop = if (format.containsKey("crop-top")) format.getInteger("crop-top") else 0
        val cropBottom = if (format.containsKey("crop-bottom")) format.getInteger("crop-bottom") else height - 1

        val realWidth = cropRight - cropLeft + 1
        val realHeight = cropBottom - cropTop + 1

        if (videoSize.width != realWidth || videoSize.height != realHeight) {
            videoSize = IntSize(realWidth, realHeight)
            glView.queueEvent { updateMVPMatrix() }
        }
    }

    private fun setupDecoder(mime: String) {
        val st = surfaceTexture ?: return
        try {
            decoder = MediaCodec.createDecoderByType(mime).apply {
                val format = MediaFormat.createVideoFormat(mime, 1280, 720)
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
                configure(format, Surface(st), null, 0)
                start()
            }
            isDecoderStarted = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Renderer Overrides ---

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = createProgram(VERTEX_SHADER_SOURCE, FRAGMENT_SHADER_SOURCE)
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())

        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(this@StreamRenderer)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewSize = IntSize(width, height)
        GLES20.glViewport(0, 0, width, height)
        updateMVPMatrix()
    }

    override fun onDrawFrame(gl: GL10?) {
        synchronized(this) {
            if (isSurfaceAvailable) {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(stMatrix)
                isSurfaceAvailable = false
            }
        }
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, stMatrix, 0)

        val posH = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glVertexAttribPointer(posH, 2, GLES20.GL_FLOAT, false, 8, VERTEX_BUFFER)

        val texH = GLES20.glGetAttribLocation(program, "aTextureCoord")
        GLES20.glEnableVertexAttribArray(texH)
        GLES20.glVertexAttribPointer(texH, 2, GLES20.GL_FLOAT, false, 8, TEX_BUFFER)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        isSurfaceAvailable = true
        glView.requestRender()
    }

    private fun updateMVPMatrix() {
        Matrix.setIdentityM(mvpMatrix, 0)
        val (vW, vH) = videoSize
        val (sW, sH) = viewSize
        if (vW == 0 || vH == 0 || sW == 0 || sH == 0) return

        val vRatio = vW.toFloat() / vH
        val sRatio = sW.toFloat() / sH

        when (scaleType) {
            ScaleType.FitCenter -> {
                if (vRatio > sRatio) Matrix.scaleM(mvpMatrix, 0, 1f, sRatio / vRatio, 1f)
                else Matrix.scaleM(mvpMatrix, 0, vRatio / sRatio, 1f, 1f)
            }
            ScaleType.CenterCrop -> {
                if (vRatio > sRatio) Matrix.scaleM(mvpMatrix, 0, vRatio / sRatio, 1f, 1f)
                else Matrix.scaleM(mvpMatrix, 0, 1f, sRatio / vRatio, 1f)
            }
            ScaleType.Original -> {
                Matrix.scaleM(mvpMatrix, 0, vW.toFloat() / sW, vH.toFloat() / sH, 1f)
            }
            ScaleType.Stretch -> {}
        }
    }

    private fun createProgram(v: String, f: String): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, v)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, f)
        return GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vs)
            GLES20.glAttachShader(this, fs)
            GLES20.glLinkProgram(this)
        }
    }

    private fun loadShader(type: Int, source: String): Int = GLES20.glCreateShader(type).apply {
        GLES20.glShaderSource(this, source)
        GLES20.glCompileShader(this)
    }

    fun release() {
        isDecoderStarted = false
        decoder?.run { try { stop(); release() } catch (_: Exception) {} }
        decoder = null
        surfaceTexture?.release()
    }
}