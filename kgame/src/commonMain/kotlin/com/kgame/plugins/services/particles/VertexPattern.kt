package com.kgame.plugins.services.particles

class VertexPattern(
    private val layer: ParticleLayer,
    private val context: ParticleContext
) {
    // One-time capacity calculation: 1 particle = 2 triangles = 6 vertices
    private val capacity = layer.count * 6

    // --- Rendering Buffers (Primitive arrays for zero-allocation rendering) ---
    // x1, y1, x2, y2... (Capacity * 2)
    internal val positions = FloatArray(capacity * 2)
    // ARGB packed integers (Capacity)
    internal val colors = IntArray(capacity)
    // u1, v1, u2, v2... (Capacity * 2)
    internal val texCoords = FloatArray(capacity * 2)

    private val posCompiledNode: ParticleCompiledNode
    private val sizeCompiledNode: ParticleCompiledNode
    private val colorCompiledNode: ParticleCompiledNode

    private val executor = ParticleNodeExecutor()

    // --- State Buffer ---
    private val lifes = FloatArray(capacity)

    private var elapsedTime: Float = 0f

    init {
        val compiler = ParticleNodeCompiler()
        posCompiledNode = compiler.compileVector2(layer.position)
        sizeCompiledNode = compiler.compileScalar(layer.size)
        colorCompiledNode = compiler.compileColor(layer.color)

        populate()
    }

    private fun populate() {
        val count = layer.count
        val duration = layer.duration

        context.setInt(ParticleContext.COUNT, count)
        context.setFloat(ParticleContext.TIME, 0f)
        context.setFloat(ParticleContext.PROGRESS, 0f)

        var pIdx = 0
        while (pIdx < count) {
            val vBase = pIdx * 6
            context.setInt(ParticleContext.INDEX, pIdx)

            // Resolve birth state from DSL
            val resPos = executor.pickVector2(posCompiledNode, context)
            val resSize = executor.pickScalar(sizeCompiledNode, context)
            val resColor = executor.pickColor(colorCompiledNode, context)

            // Initial write of the quad
            writeQuad(vBase, resPos.x, resPos.y, resPos.x + resSize, resPos.y + resSize, duration, resColor)

            pIdx++
        }
    }

    fun update(deltaTime: Float) {
        elapsedTime += deltaTime

        val count = layer.count
        val duration = layer.duration

        // Fast culling: Stop update if layer lifetime exceeded
        if (elapsedTime > duration) {
            return
        }

        // --- Frame-level context setup (Executed once per frame) ---
        context.setFloat(ParticleContext.TIME, elapsedTime)
        context.setFloat(ParticleContext.DELTA_TIME, deltaTime)
        context.setInt(ParticleContext.COUNT, count)
        context.setFloat(ParticleContext.PROGRESS, elapsedTime / duration)

        var pIdx = 0
        while (pIdx < count) {
            val vBase = pIdx * 6
            val currentLife = lifes[vBase]

            // Only update active vertices to save CPU cycles
            if (currentLife > 0f) {
                context.setInt(ParticleContext.INDEX, pIdx)

                // --- Execute compiled bytecode for high-speed resolution ---

                // Resolve Position: Result stored at stack[0]=x, stack[1]=y
                val resPos = executor.pickVector2(posCompiledNode, context)
                val resSize = executor.pickScalar(sizeCompiledNode, context)
                val resColor = executor.pickColor(colorCompiledNode, context)
                val nextLife = currentLife - deltaTime

                // Update the quad data using inlined writeQuad
                writeQuad(vBase, resPos.x, resPos.y, resPos.x + resSize, resPos.y + resSize, nextLife, resColor)
            }
            pIdx++
        }
    }

    fun updateLegacy(deltaTime: Float) {
        elapsedTime += deltaTime

        val count = layer.count
        val duration = layer.duration

        // Fast culling: Stop update if layer lifetime exceeded
        if (elapsedTime > duration) {
            return
        }

        context.setFloat(ParticleContext.TIME, elapsedTime)
        context.setFloat(ParticleContext.DELTA_TIME, deltaTime)
        context.setInt(ParticleContext.COUNT, count)
        context.setFloat(ParticleContext.PROGRESS, elapsedTime / duration)

        var pIdx = 0
        while (pIdx < count) {
            val vBase = pIdx * 6
            val currentLife = lifes[vBase]

            // Only update active vertices to save CPU cycles
            if (currentLife > 0f) {
                context.setInt(ParticleContext.INDEX, pIdx)

                // Resolve current frame state from DSL nodes
                val resPos = ParticleNodeResolver.resolveVector2(layer.position, context)
                val resSize = ParticleNodeResolver.resolveScalar(layer.size, context)
                val resColor = ParticleNodeResolver.resolveColor(layer.color, context)
                val nextLife = currentLife - deltaTime

                // Update the quad data in primitive buffers
                writeQuad(vBase, resPos.x, resPos.y, resPos.x + resSize, resPos.y + resSize, nextLife, resColor)
            }
            pIdx++
        }
    }

    /**
     * Helper to write a 6-vertex quad (2 triangles) into primitive buffers.
     * Triangle 1: TL, TR, BL | Triangle 2: TR, BR, BL
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun writeQuad(vBase: Int, left: Float, top: Float, right: Float, bottom: Float, life: Float, color: Int) {
        // Triangle 1: TL(0,0), TR(1,0), BL(0,1)
        writeVertex(vBase,     left,  top,    0f, 0f, life, color)
        writeVertex(vBase + 1, right, top,    1f, 0f, life, color)
        writeVertex(vBase + 2, left,  bottom, 0f, 1f, life, color)

        // Triangle 2: TR(1,0), BR(1,1), BL(0,1)
        writeVertex(vBase + 3, right, top,    1f, 0f, life, color)
        writeVertex(vBase + 4, right, bottom, 1f, 1f, life, color)
        writeVertex(vBase + 5, left,  bottom, 0f, 1f, life, color)
    }

    /**
     * Write single vertex data directly into Float/Int arrays.
     * Prevents object allocation (Offset, Color) during the render loop.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun writeVertex(idx: Int, x: Float, y: Float, u: Float, v: Float, lf: Float, c: Int) {
        // Position buffer: [x, y, x, y, ...]
        val pBase = idx shl 1 // Equivalent to idx * 2
        positions[pBase] = x
        positions[pBase + 1] = y

        // Color buffer: [argb, argb, ...]
        colors[idx] = c

        // Texture coordinates buffer: [u, v, u, v, ...]
        val tBase = idx shl 1
        texCoords[tBase] = u
        texCoords[tBase + 1] = v

        // Internal state
        lifes[idx] = lf
    }
}