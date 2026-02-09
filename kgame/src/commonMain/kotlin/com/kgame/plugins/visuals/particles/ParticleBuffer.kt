package com.kgame.plugins.visuals.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.toArgb

/**
 * A syntax-sugar layer to populate [ActiveEffect] without manual index math.
 * All English documentation for consistency.
 */
class ParticleBuffer internal constructor(private val effect: ActiveEffect) {

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

fun interface ParticlePattern {
    fun onPopulate(count: Int, buffer: ParticleBuffer)
}

internal class ActiveEffect(val capacity: Int = 2048) {
    val posList = OffsetArrayList(capacity)
    val colorList = ColorArrayList(capacity)
    val velocityX = FloatArray(capacity)
    val velocityY = FloatArray(capacity)
    val life = FloatArray(capacity)
    val friction = FloatArray(capacity)
    val gravity = FloatArray(capacity)

    val bufferContext = ParticleBuffer(this)
    private var cachedVertices: Vertices? = null
    private var currentMode: VertexMode? = null

    var isDead: Boolean = true

    /** This is the "Cursor". It tracks exactly how many vertices were written. */
    var activeCount: Int = 0
        private set

    /**
     * Prepares the effect for a new spawn pass.
     */
    fun reset(mode: VertexMode = VertexMode.Triangles) {
        this.activeCount = 0 // Reset cursor to start
        this.currentMode = mode
        this.isDead = false
    }

    /**
     * The internal writer called by ParticleBuffer.
     * Logic is centralized here to protect the arrays.
     */
    fun writeRaw(x: Float, y: Float, vx: Float, vy: Float, l: Float, f: Float, g: Float, c: Int) {
        if (activeCount >= capacity) return

        posList.set(activeCount, x, y)
        colorList.set(activeCount, c)
        velocityX[activeCount] = vx
        velocityY[activeCount] = vy
        life[activeCount] = l
        friction[activeCount] = f
        gravity[activeCount] = g

        activeCount++
    }

    fun getVertices(): Vertices {
        val mode = currentMode ?: VertexMode.Triangles
        val existing = cachedVertices
        return if (existing == null || existing.vertexMode != mode) {
            Vertices(
                vertexMode = mode,
                positions = posList,
                colors = colorList,
                textureCoordinates = emptyList(),
                indices = emptyList()
            ).also { cachedVertices = it }
        } else existing
    }

    fun update(dt: Float) {
        var anyStillAlive = false
        var i = 0
        // Only loop through what was actually put in the buffer
        while (i < activeCount) {
            if (life[i] > 0f) {
                velocityX[i] *= friction[i]
                velocityY[i] = (velocityY[i] * friction[i]) + gravity[i]
                posList.set(i, posList.getRawX(i) + velocityX[i], posList.getRawY(i) + velocityY[i])
                life[i] -= dt
                if (life[i] > 0f) anyStillAlive = true
            }
            i++
        }
        isDead = !anyStillAlive
    }
}