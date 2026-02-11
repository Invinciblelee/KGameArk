package com.kgame.plugins.services.particles

import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices

class ParticlePattern(
    private val scope: ParticleNodeScope
) {
    // One-time capacity calculation
    private val capacity = scope.layers.sumOf { it.spawnCount * 6 }

    private val context = scope.context

    // --- Rendering Buffers ---
    internal val positionList = OffsetArrayList(capacity)
    internal val colorList = ColorArrayList(capacity)
    internal val dummyTextureCoordinates = OffsetArrayList(capacity)

    // --- State Buffer ---
    private val lifes = FloatArray(capacity)

    private var elapsedTime: Float = 0f

    init {
        populateAll()
    }

    private fun populateAll() {
        val layers = scope.layers
        val layerSize = layers.size
        var layerIdx = 0
        var vertexIdx = 0

        while (layerIdx < layerSize) {
            val layer = layers[layerIdx]
            val count = layer.spawnCount
            val duration = layer.duration

            context.setInt(ParticleContext.COUNT, count)
            context.setFloat(ParticleContext.TIME, 0f)
            context.setFloat(ParticleContext.PROGRESS, 0f)

            var pIdx = 0
            while (pIdx < count) {
                val vBase = vertexIdx + (pIdx * 6)
                context.setInt(ParticleContext.INDEX, pIdx)

                // Resolve birth state
                val pos = CpuNodeResolver.resolveVector2(layer.position, context)
                val argb = CpuNodeResolver.resolveColor(layer.color, context)
                val size = CpuNodeResolver.resolveScalar(layer.size, context)

                // Use the new private method for clean initialization
                writeQuad(vBase, pos.x, pos.y, pos.x + size, pos.y + size, duration, argb)

                pIdx++
            }
            vertexIdx += count * 6
            layerIdx++
        }
    }

    fun update(dt: Float) {
        elapsedTime += dt
        context.setFloat(ParticleContext.TIME, elapsedTime)
        context.setFloat(ParticleContext.DELTA_TIME, dt)

        val layers = scope.layers
        val layerSize = layers.size
        var layerIdx = 0
        var vertexIdx = 0

        while (layerIdx < layerSize) {
            val layer = layers[layerIdx]
            val count = layer.spawnCount
            val duration = layer.duration

            // Fast culling
            if (elapsedTime > duration) {
                vertexIdx += count * 6
                layerIdx++
                continue
            }

            context.setInt(ParticleContext.COUNT, count)
            context.setFloat(ParticleContext.PROGRESS, elapsedTime / duration)

            var pIdx = 0
            while (pIdx < count) {
                val vBase = vertexIdx + (pIdx * 6)

                if (lifes[vBase] > 0f) {
                    context.setInt(ParticleContext.INDEX, pIdx)

                    // Resolve current frame from DSL
                    val resPos = CpuNodeResolver.resolveVector2(layer.position, context)
                    val resSize = CpuNodeResolver.resolveScalar(layer.size, context)
                    val resColor = CpuNodeResolver.resolveColor(layer.color, context)
                    val resAlpha = CpuNodeResolver.resolveScalar(layer.alpha, context)

                    val alphaInt = (resAlpha.coerceIn(0f, 1f) * 255f).toInt()
                    val colorInt = (resColor and 0x00FFFFFF) or (alphaInt shl 24)
                    val nextLife = lifes[vBase] - dt

                    // Clean update call
                    writeQuad(vBase, resPos.x, resPos.y, resPos.x + resSize, resPos.y + resSize, nextLife, colorInt)
                }
                pIdx++
            }
            vertexIdx += count * 6
            layerIdx++
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
fun Vertices(pattern: ParticlePattern): Vertices {
    return Vertices(
        vertexMode = VertexMode.Triangles,
        positions = pattern.positionList,
        colors = pattern.colorList,
        textureCoordinates = pattern.dummyTextureCoordinates,
        indices = emptyList()
    )
}