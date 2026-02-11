package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import com.kgame.engine.geometry.Vector2

class VertexPattern(
    private val layer: ParticleLayer,
    private val context: ParticleContext
) {
    // One-time capacity calculation
    private val capacity = layer.count * 6

    // --- Rendering Buffers ---
    internal val positionList = OffsetArrayList(capacity)
    internal val colorList = ColorArrayList(capacity)
    internal val dummyTextureCoordinates = OffsetArrayList(capacity)

    // --- State Buffer ---
    private val lifes = FloatArray(capacity)

    private var elapsedTime: Float = 0f

    init {
        populate()
    }

    private fun populate() {
        val count = layer.count
        val duration = layer.duration

        context.setInt(ParticleContext.COUNT, count)
        context.setFloat(ParticleContext.TIME, 0f)
        context.setFloat(ParticleContext.PROGRESS, 0f)
        context.setVector2(ParticleContext.RESOLUTION, Vector2(layer.frame.size.packedValue))

        var pIdx = 0
        while (pIdx < count) {
            val vBase = pIdx * 6
            context.setInt(ParticleContext.INDEX, pIdx)

            // Resolve birth state
            val pos = CpuNodeResolver.resolveVector2(layer.position, context)
            val argb = CpuNodeResolver.resolveColor(layer.color, context)
            val size = CpuNodeResolver.resolveScalar(layer.size, context)

            // Use the new private method for clean initialization
            writeQuad(vBase, pos.x, pos.y, pos.x + size, pos.y + size, duration, argb)

            pIdx++
        }
    }

    fun setResolution(size: Size) {
        context.setVector2(ParticleContext.RESOLUTION, Vector2(size.packedValue))
    }

    fun update(dt: Float) {
        elapsedTime += dt

        val count = layer.count
        val duration = layer.duration

        // Fast culling
        if (elapsedTime > duration) {
            return
        }

        context.setFloat(ParticleContext.TIME, elapsedTime)
        context.setFloat(ParticleContext.DELTA_TIME, dt)
        context.setInt(ParticleContext.COUNT, count)
        context.setFloat(ParticleContext.PROGRESS, elapsedTime / duration)
        context.setVector2(ParticleContext.RESOLUTION, Vector2(layer.frame.size.packedValue))

        var pIdx = 0
        while (pIdx < count) {
            val vBase = pIdx * 6

            if (lifes[vBase] > 0f) {
                context.setInt(ParticleContext.INDEX, pIdx)

                // Resolve current frame from DSL
                val resPos = CpuNodeResolver.resolveVector2(layer.position, context)
                val resSize = CpuNodeResolver.resolveScalar(layer.size, context)
                val resColor = CpuNodeResolver.resolveColor(layer.color, context)
                val nextLife = lifes[vBase] - dt

                // Clean update call
                writeQuad(vBase, resPos.x, resPos.y, resPos.x + resSize, resPos.y + resSize, nextLife, resColor)
            }
            pIdx++
        }
    }

    /**
     * Helper to write a 6-vertex quad (2 triangles) into primitive buffers.
     * Triangle 1: TL, TR, BL | Triangle 2: TR, BR, BL
     */
    private fun writeQuad(vBase: Int, left: Float, top: Float, right: Float, bottom: Float, life: Float, color: Int) {
        // Vertex 0: Top-Left
        writeVertex(vBase, left, top, life, color)
        // Vertex 1: Top-Right
        writeVertex(vBase + 1, right, top, life, color)
        // Vertex 2: Bottom-Left
        writeVertex(vBase + 2, left, bottom, life, color)

        // Vertex 3: Top-Right (Same as 1)
        writeVertex(vBase + 3, right, top, life, color)
        // Vertex 4: Bottom-Right
        writeVertex(vBase + 4, right, bottom, life, color)
        // Vertex 5: Bottom-Left (Same as 2)
        writeVertex(vBase + 5, left, bottom, life, color)
    }

    private fun writeVertex(idx: Int, x: Float, y: Float, lf: Float, c: Int) {
        positionList.set(idx, x, y)
        lifes[idx] = lf
        colorList.set(idx, c)
        // Ensure dummy coords are initialized at least once in populateAll or here
        dummyTextureCoordinates.set(idx, 0f, 0f)
    }
}

/**
 * Create Vertices directly from the Pattern's buffers.
 */
fun Vertices(pattern: VertexPattern): Vertices {
    return Vertices(
        vertexMode = VertexMode.Triangles,
        positions = pattern.positionList,
        colors = pattern.colorList,
        textureCoordinates = pattern.dummyTextureCoordinates,
        indices = emptyList()
    )
}