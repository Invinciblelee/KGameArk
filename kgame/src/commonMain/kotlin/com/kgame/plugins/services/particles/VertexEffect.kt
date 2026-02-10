package com.kgame.plugins.services.particles

import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices

internal class VertexEffect(val capacity: Int = 65536) {
    val positionList = OffsetArrayList(capacity)
    val colorList = ColorArrayList(capacity)
    val velocityX = FloatArray(capacity)
    val velocityY = FloatArray(capacity)
    val life = FloatArray(capacity)
    val friction = FloatArray(capacity)
    val gravity = FloatArray(capacity)

    private val dummyTextureCoordinates = OffsetArrayList(capacity)
    val buffer = ParticleBuffer(this)

    var activeCount: Int = 0
    var elapsedTime: Float = 0f

    private var isDirty = true
    private var cachedVertices: Vertices? = null
    private var currentScope: ParticleNodeScope? = null

    fun reset(scope: ParticleNodeScope) {
        this.activeCount = 0
        this.elapsedTime = 0f
        this.currentScope = scope
        this.isDirty = true
        this.cachedVertices = null
    }

    fun obtain(): Vertices {
        val current = cachedVertices
        if (!isDirty && current != null) return current

        val newVertices = Vertices(
            vertexMode = VertexMode.Triangles,
            positions = positionList.limit(activeCount),
            colors = colorList.limit(activeCount),
            textureCoordinates = dummyTextureCoordinates.limit(activeCount),
            indices = emptyList()
        )
        cachedVertices = newVertices
        isDirty = false
        return newVertices
    }

    /**
     * Main update entry point.
     * Iterates through layers and particles using manual while-loops.
     */
    fun update(dt: Float) {
        val scope = currentScope ?: return
        val frameWeight = dt * 60f
        elapsedTime += dt

        var currentVertexIdx = 0
        val layers = scope.layers
        val layerSize = layers.size
        var layerIdx = 0

        while (layerIdx < layerSize) {
            val layer = layers[layerIdx]
            val layerParticleCount = layer.count
            val progress = if (layer.duration > 0f) (elapsedTime / layer.duration).coerceIn(0f, 1f) else 1f

            var pIdx = 0
            while (pIdx < layerParticleCount) {
                val vBase = currentVertexIdx + (pIdx * 6)
                if (life[vBase] > 0f) {
                    updateParticle(vBase, pIdx, layer, dt, frameWeight, progress)
                }
                pIdx++
            }
            currentVertexIdx += layerParticleCount * 6
            layerIdx++
        }
        isDirty = true
    }

    /**
     * Handles individual particle physics and node evaluation.
     */
    private fun updateParticle(
        vBase: Int,
        pIdx: Int,
        layer: ParticleLayer,
        dt: Float,
        frameWeight: Float,
        progress: Float
    ) {
        // 1. Physics: Velocity and Position evolution
        val f = friction[vBase]
        val g = gravity[vBase]

        val vx = velocityX[vBase] * f
        val vy = velocityY[vBase] * f + g

        velocityX[vBase] = vx
        velocityY[vBase] = vy

        val dx = vx * frameWeight
        val dy = vy * frameWeight
        val nextLife = life[vBase] - dt

        // 2. Aesthetics: Evaluating dynamic nodes (Alpha & Color)
        val nodeAlpha = evaluateScalar(layer.alpha, pIdx, progress)
        val argb = evaluateColor(layer.color, pIdx, progress)

        val alphaInt = (nodeAlpha * 255f).toInt()
        val alphaBits = (if (alphaInt < 0) 0 else if (alphaInt > 255) 255 else alphaInt) shl 24
        val finalColor = (argb and 0x00FFFFFF) or alphaBits

        // 3. Buffer Write: Updating the 6 vertices in the quad
        writeQuadVertices(vBase, dx, dy, nextLife, finalColor)
    }

    /**
     * Batch writes updated data to the internal primitive lists.
     */
    private fun writeQuadVertices(vBase: Int, dx: Float, dy: Float, nextLife: Float, finalColor: Int) {
        var offset = 0
        while (offset < 6) {
            val target = vBase + offset
            positionList.set(
                target,
                positionList.getX(target) + dx,
                positionList.getY(target) + dy
            )
            life[target] = nextLife
            colorList.set(target, finalColor)
            offset++
        }
    }

    private fun evaluateScalar(node: ParticleNode, index: Int, progress: Float): Float {
        return when (node) {
            is ParticleNode.Scalar -> node.value
            is ParticleNode.RandomRange -> node.min
            is ParticleNode.IndexMod -> {
                if (index % node.divisor == 0) evaluateScalar(node.onTrue, index, progress)
                else evaluateScalar(node.onFalse, index, progress)
            }
            is ParticleNode.Add -> evaluateScalar(node.left, index, progress) + evaluateScalar(node.right, index, progress)
            is ParticleNode.Multiply -> evaluateScalar(node.left, index, progress) * evaluateScalar(node.right, index, progress)
            is ParticleNode.Time -> elapsedTime
            is ParticleNode.Color -> ((node.argb shr 24) and 0xFF) / 255f
            else -> 1f
        }
    }

    private fun evaluateColor(node: ParticleNode, index: Int, progress: Float): Int {
        return when (node) {
            is ParticleNode.Color -> node.argb
            is ParticleNode.IndexMod -> {
                if (index % node.divisor == 0) evaluateColor(node.onTrue, index, progress)
                else evaluateColor(node.onFalse, index, progress)
            }
            else -> 0xFFFFFFFF.toInt()
        }
    }
}