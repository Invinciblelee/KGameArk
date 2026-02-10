package com.kgame.plugins.services.particles

import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Clock

class ParticlePattern(
    private val scope: ParticleNodeScope
) {
    // Exact count based on scope: no need for capacity or activeCount tracking
    internal val requiredCount = scope.layers.sumOf { it.count } * 6

    // --- Rendering Buffers ---
    internal val positionList = OffsetArrayList(requiredCount)
    internal val colorList = ColorArrayList(requiredCount)
    internal val dummyTextureCoordinates = OffsetArrayList(requiredCount)

    // --- Physical State Buffers ---
    private val life = FloatArray(requiredCount)
    private val velocityX = FloatArray(requiredCount)
    private val velocityY = FloatArray(requiredCount)
    private val friction = FloatArray(requiredCount)
    private val gravity = FloatArray(requiredCount)

    // --- Gene Buffers (Captured at birth) ---
    private val baseSizes = FloatArray(requiredCount)
    private val baseColors = IntArray(requiredCount)
    private val fullLife = FloatArray(requiredCount)

    private var elapsedTime: Float = 0f
    private val args = ParticleArgs()

    init {
        // Encapsulated data population during construction
        populateAll()
    }

    /**
     * Stage 1: Population (Private)
     * Mirrors the 'writeRaw' logic but resolves everything from the DSL scope.
     */
    private fun populateAll() {
        val layers = scope.layers
        val layerSize = layers.size
        var layerIdx = 0
        var vertexIdx = 0

        val random = Random(Clock.System.now().toEpochMilliseconds())

        while (layerIdx < layerSize) {
            val layer = layers[layerIdx]
            val count = layer.count
            args.setInt(ParticleArgs.COUNT, count)

            var pIdx = 0
            while (pIdx < count) {
                val vBase = vertexIdx + (pIdx * 6)
                args.setInt(ParticleArgs.INDEX, pIdx)
                args.setFloat(ParticleArgs.TIME, 0f)
                args.setFloat(ParticleArgs.PROGRESS, 0f)

                // Resolve birth-state traits
                val speed = CpuNodeResolver.resolveScalar(layer.velocity, args)
                val angle = CpuNodeResolver.resolveScalar(layer.angle, args)

                val pos = CpuNodeResolver.resolveVector2(layer.position, args)
                val argb = CpuNodeResolver.resolveColor(layer.color, args)
                val fric = CpuNodeResolver.resolveScalar(layer.friction, args)
                val grav = CpuNodeResolver.resolveScalar(layer.gravity, args)
                val initialSize = CpuNodeResolver.resolveScalar(layer.size, args)

                val vx = cos(angle) * speed
                val vy = sin(angle) * speed

                // Populate the 6-vertex block
                fillVertexBlock(vBase, pos.x, pos.y, vx, vy, layer.duration, fric, grav, argb, initialSize)

                pIdx++
            }
            vertexIdx += count * 6
            layerIdx++
        }
    }

    /**
     * Stage 2: Evolution (Public)
     * Stateless 0GC update using the same logic as your VertexEffect.
     */
    fun update(dt: Float) {
        elapsedTime += dt
        args.setFloat(ParticleArgs.TIME, elapsedTime)

        val layers = scope.layers
        val layerSize = layers.size
        var layerIdx = 0
        var vertexIdx = 0

        while (layerIdx < layerSize) {
            val layer = layers[layerIdx]
            val progress = if (layer.duration > 0f) (elapsedTime / layer.duration).coerceIn(0f, 1f) else 1f
            val count = layer.count

            args.setInt(ParticleArgs.COUNT, count)
            args.setFloat(ParticleArgs.PROGRESS, progress)

            var pIdx = 0
            while (pIdx < count) {
                val vBase = vertexIdx + (pIdx * 6)

                if (life[vBase] > 0f) {
                    args.setInt(ParticleArgs.INDEX, pIdx)

                    // Resolve per-frame evolving properties
                    val alpha = CpuNodeResolver.resolveScalar(layer.alpha, args)
                    val nodeColor = CpuNodeResolver.resolveColor(layer.color, args)
                    val currentSize = CpuNodeResolver.resolveScalar(layer.size, args)

                    val alphaInt = (alpha.coerceIn(0f, 1f) * 255f).toInt()
                    val color = (nodeColor and 0x00FFFFFF) or (alphaInt shl 24)

                    // Physics and Quad Remapping
                    evolveParticle(dt, vBase, currentSize, color)
                }
                pIdx++
            }
            vertexIdx += count * 6
            layerIdx++
        }
    }

    private fun evolveParticle(dt: Float, vBase: Int, size: Float, color: Int) {
        // 1. Physics: Velocity evolution
        val f = friction[vBase]
        val g = gravity[vBase]

        val vx = velocityX[vBase] * f
        val vy = velocityY[vBase] * f + g

        velocityX[vBase] = vx
        velocityY[vBase] = vy

        // 2. Displacement: Move anchor (Vertex 0)
        val nextX = positionList.getX(vBase) + vx * dt
        val nextY = positionList.getY(vBase) + vy * dt
        val nextLife = life[vBase] - dt

        // 3. Quad Sync: TL, TR, BL, TR, BR, BL
        val r = nextX + size
        val b = nextY + size


        syncQuad(vBase, nextX, nextY, r, b, nextLife, color)
    }

    private fun syncQuad(vBase: Int, x: Float, y: Float, r: Float, b: Float, lf: Float, c: Int) {
        writeVertex(vBase,     x, y, lf, c)
        writeVertex(vBase + 1, r, y, lf, c)
        writeVertex(vBase + 2, x, b, lf, c)
        writeVertex(vBase + 3, r, y, lf, c)
        writeVertex(vBase + 4, r, b, lf, c)
        writeVertex(vBase + 5, x, b, lf, c)
    }

    private fun writeVertex(idx: Int, x: Float, y: Float, lf: Float, c: Int) {
        positionList.set(idx, x, y)
        life[idx] = lf
        colorList.set(idx, c)
    }

    private fun fillVertexBlock(
        vBase: Int, x: Float, y: Float, vx: Float, vy: Float,
        lf: Float, fric: Float, grav: Float, argb: Int, sz: Float
    ) {
        var i = 0
        while (i < 6) {
            val idx = vBase + i
            positionList.set(idx, x, y)
            velocityX[idx] = vx
            velocityY[idx] = vy
            life[idx] = lf
            friction[idx] = fric
            gravity[idx] = grav
            colorList.set(idx, argb)
            fullLife[idx] = lf
            baseColors[idx] = argb
            baseSizes[idx] = sz
            dummyTextureCoordinates.set(idx, 0f, 0f)
            i++
        }
    }
}

/**
 * Create Vertices directly from the Pattern's buffers.
 */
fun Vertices(pattern: ParticlePattern): Vertices {
    return Vertices(
        vertexMode = VertexMode.Triangles,
        positions = pattern.positionList.limit(pattern.requiredCount),
        colors = pattern.colorList.limit(pattern.requiredCount),
        textureCoordinates = pattern.dummyTextureCoordinates.limit(pattern.requiredCount),
        indices = emptyList()
    )
}