package com.kgame.plugins.services.particles

class VertexPattern(
    private val layer: ParticleLayer,
    private val context: ParticleContext
) {
    // 1 Particle = 4 Vertices (TL, TR, BR, BL)
    private val vertexCount = layer.count * 4
    // 1 Particle = 2 Triangles = 6 Indices
    private val indexCount = layer.count * 6

    // --- Primitive Buffers (0 GC) ---
    internal val positions = FloatArray(vertexCount * 2)
    internal val colors = IntArray(vertexCount)
    internal val texCoords = FloatArray(vertexCount * 2)
    internal val indices = ShortArray(indexCount)
    private val lifes = FloatArray(vertexCount)

    // --- Compiled Executables ---
    private val posExec: VectorExec
    private val sizeExec: ScalarExec
    private val colorExec: ColorExec

    private var elapsedTime: Float = 0f

    init {
        val compiler = ParticleNodeCompiler()
        posExec = compiler.compileVector(layer.position)
        sizeExec = compiler.compileScalar(layer.size)
        colorExec = compiler.compileColor(layer.color)

        populate()
    }

    /**
     * Initial setup pass. Resolves birth state and initializes static buffers.
     */
    fun populate() {
        val count = layer.count
        val duration = layer.duration

        context.reset()

        var pIdx = 0
        while (pIdx < count) {
            val vBase = pIdx shl 2 // pIdx * 4
            val iBase = pIdx * 6
            context.seek(pIdx)

            // 1. Setup Static Indices (Triangle 1: 0,1,2 | Triangle 2: 0,2,3)
            indices[iBase] = vBase.toShort()
            indices[iBase + 1] = (vBase + 1).toShort()
            indices[iBase + 2] = (vBase + 2).toShort()
            indices[iBase + 3] = vBase.toShort()
            indices[iBase + 4] = (vBase + 2).toShort()
            indices[iBase + 5] = (vBase + 3).toShort()

            // 2. Setup Static UVs
            val t = vBase shl 1
            texCoords[t] = 0f;   texCoords[t + 1] = 0f // TL
            texCoords[t + 2] = 1f; texCoords[t + 3] = 0f // TR
            texCoords[t + 4] = 1f; texCoords[t + 5] = 1f // BR
            texCoords[t + 6] = 0f; texCoords[t + 7] = 1f // BL

            // 3. Resolve and write initial state
            val resPos = posExec.eval(context)
            val resSize = sizeExec.eval(context)
            val resColor = colorExec.eval(context)

            writeVertexData(vBase, resPos.x, resPos.y, resSize, resColor, duration)

            pIdx++
        }
    }

    /**
     * Main update loop. Optimized with localized references and 0 GC.
     */
    fun update(deltaTime: Float) {
        elapsedTime += deltaTime
        val count = layer.count
        val duration = layer.duration

        if (elapsedTime > duration) return

        // 1. Frame-level Context setup
        context.update(elapsedTime, deltaTime, elapsedTime / duration, count)

        var pIdx = 0
        while (pIdx < count) {
            val vBase = pIdx shl 2
            val currentLife = lifes[vBase]

            if (currentLife > 0f) {
                // 2. Index Switching
                context.seek(pIdx)

                // 3. Math Eval (The Compiled Executables)
                val resPos = posExec.eval(context)
                val resSize = sizeExec.eval(context)
                val resColor = colorExec.eval(context)

                // 4. Buffer Writing
                writeVertexData(vBase, resPos.x, resPos.y, resSize, resColor, currentLife - deltaTime)
            }
            pIdx++
        }
    }

    /**
     * Legacy path for validation. Replicated exactly to match Resolver logic.
     */
    fun updateLegacy(deltaTime: Float) {
        elapsedTime += deltaTime
        val count = layer.count
        val duration = layer.duration
        if (elapsedTime > duration) return

        context.update(elapsedTime, deltaTime, elapsedTime / duration, count)

        var pIdx = 0
        while (pIdx < count) {
            val vBase = pIdx shl 2
            if (lifes[vBase] > 0f) {
                context.seek(pIdx)

                val resPos = ParticleNodeResolver.resolveVector2(layer.position, context)
                val resSize = ParticleNodeResolver.resolveScalar(layer.size, context)
                val resColor = ParticleNodeResolver.resolveColor(layer.color, context)

                writeVertexData(vBase, resPos.x, resPos.y, resSize, resColor, lifes[vBase] - deltaTime)
            }
            pIdx++
        }
    }

    /**
     * High-speed vertex write logic.
     * Inlined to eliminate function call overhead while maintaining clean code.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun writeVertexData(vBase: Int, x: Float, y: Float, size: Float, color: Int, life: Float) {
        val p = vBase shl 1
        val right = x + size
        val bottom = y + size

        // Position Buffer Writes (4 vertices)
        positions[p] = x;         positions[p + 1] = y      // TL
        positions[p + 2] = right; positions[p + 3] = y      // TR
        positions[p + 4] = right; positions[p + 5] = bottom // BR
        positions[p + 6] = x;     positions[p + 7] = bottom // BL

        // Color Buffer Writes
        colors[vBase] = color;     colors[vBase + 1] = color
        colors[vBase + 2] = color; colors[vBase + 3] = color

        // State Update
        lifes[vBase] = life
    }
}