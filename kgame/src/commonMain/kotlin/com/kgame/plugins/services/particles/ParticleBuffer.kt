package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.toArgb

/**
 * A syntax-sugar layer to populate [VertexEffect] without manual index math.
 * All English documentation for consistency.
 */
class ParticleBuffer internal constructor(private val effect: VertexEffect) {

    val capacity: Int get() = effect.capacity

    /**
     * Adds a quad particle (2 triangles, 6 vertices).
     */
    fun putQuad(
        position: Offset,
        velocity: Offset,
        life: Float,
        width: Float = 10f,
        height: Float = 10f,
        friction: Float = 1f,
        gravity: Float = 0f,
        color: Color = Color.White
    ) {
        val hw = width / 2f
        val hh = height / 2f
        val argb = color.toArgb()

        val l = position.x - hw
        val r = position.x + hw
        val t = position.y - hh
        val b = position.y + hh

        // Triangle 1: TL, TR, BL
        effect.writeRaw(l, t, velocity.x, velocity.y, life, friction, gravity, argb)
        effect.writeRaw(r, t, velocity.x, velocity.y, life, friction, gravity, argb)
        effect.writeRaw(l, b, velocity.x, velocity.y, life, friction, gravity, argb)

        // Triangle 2: TR, BR, BL
        effect.writeRaw(r, t, velocity.x, velocity.y, life, friction, gravity, argb)
        effect.writeRaw(r, b, velocity.x, velocity.y, life, friction, gravity, argb)
        effect.writeRaw(l, b, velocity.x, velocity.y, life, friction, gravity, argb)
    }

    /**
     * Adds a single triangle (3 vertices).
     */
    fun putTriangle(
        p1: Offset, p2: Offset, p3: Offset,
        velocity: Offset,
        life: Float,
        friction: Float = 1f,
        gravity: Float = 0f,
        color: Color = Color.White
    ) {
        val argb = color.toArgb()
        effect.writeRaw(p1.x, p1.y, velocity.x, velocity.y, life, friction, gravity, argb)
        effect.writeRaw(p2.x, p2.y, velocity.x, velocity.y, life, friction, gravity, argb)
        effect.writeRaw(p3.x, p3.y, velocity.x, velocity.y, life, friction, gravity, argb)
    }
}