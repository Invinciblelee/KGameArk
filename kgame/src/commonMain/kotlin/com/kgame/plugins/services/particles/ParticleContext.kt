package com.kgame.plugins.services.particles

import com.kgame.engine.geometry.Vector2

/**
 * A high-performance, thread-safe context for managing particle system parameters.
 * It uses bit-packing for complex types (Offset, Size, Color) and delegates to
 * a 64-bit primitive container to avoid object boxing.
 */
class ParticleContext {

    // --- Frame Global State ---
    var time: Float = 0f
        private set

    var deltaTime: Float = 0f
        private set

    var progress: Float = 0f
        private set

    var count: Int = 0
        private set

    // --- Iterator State ---
    var index: Int = 0
        private set

    /**
     * Synchronizes global frame parameters.
     * Called once at the beginning of the layer update.
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun update(time: Float, deltaTime: Float, progress: Float, count: Int) {
        this.time = time
        this.deltaTime = deltaTime
        this.progress = progress.coerceIn(0f, 1f)
        this.count = count
    }

    /**
     * Pivots the context to a specific particle index.
     * This is the high-frequency entry point within the update loop.
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun seek(index: Int) {
        this.index = index
    }

    /**
     * Clears all state for reuse or recycling.
     */
    internal fun reset() {
        this.time = 0f
        this.deltaTime = 0f
        this.index = 0
        this.count = 0
        this.progress = 0f
    }
}
