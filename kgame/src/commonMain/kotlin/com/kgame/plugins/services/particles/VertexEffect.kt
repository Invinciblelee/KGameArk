package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Vertices

class VertexEffect(val pattern: VertexPattern) {
    var elapsedTime: Float = 0f
        private set

    private var size: Size = Size.Zero

    private var isDirty = true
    private var cachedVertices: Vertices? = null

    /**
     * Updates particle state.
     * Uses the first vertex of each quad as the physics anchor.
     */
    fun update(dt: Float) {
        elapsedTime += dt

        pattern.update(dt)
        isDirty = true
    }

    /**
     * Updates canvas resolution.
     */
    fun setResolution(size: Size) {
        if (this.size == size || size.isEmpty()) return
        this.size = size
        pattern.setResolution(size)
        isDirty = true
    }

    /**
     * Obtains the Vertices object for rendering.
     */
    fun obtain(): Vertices {
        val current = cachedVertices
        if (!isDirty && current != null) return current

        val newVertices = Vertices(pattern)
        cachedVertices = newVertices
        isDirty = false
        return newVertices
    }

}

