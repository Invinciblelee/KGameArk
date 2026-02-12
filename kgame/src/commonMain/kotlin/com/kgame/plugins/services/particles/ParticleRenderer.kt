package com.kgame.plugins.services.particles

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextPainter.paint
import com.kgame.engine.graphics.drawscope.drawVertices
import com.kgame.engine.graphics.material.MaterialEffect

class ParticleRenderer(
    val pattern: VertexPattern,
    val effect: MaterialEffect?,
    val frame: Rect,
    val duration: Float
) {
    private var elapsedTime: Float = 0f
    private var size: Size = Size.Zero

    val isDead: Boolean get() = elapsedTime >= duration

    /**
     * Updates particle state.
     * Uses the first vertex of each quad as the physics anchor.
     */
    fun update(dt: Float) {
        elapsedTime += dt

        if (elapsedTime > duration) return

        pattern.update(dt)
        effect?.update(dt)
    }

    /**
     * Updates canvas resolution.
     */
    fun setResolution(size: Size) {
        if (this.size == size || size.isEmpty()) return
        this.size = size
        effect?.setResolution(size)
    }

    fun render(drawScope: DrawScope, paint: Paint) {
        if (elapsedTime > duration) return

        effect?.applyTo(size, paint)

        val center = frame.center
        drawScope.withTransform({
            translate(center.x, center.y)
        }) {
            drawVertices(
                vertexMode = VertexMode.Triangles,
                positions = pattern.positions,
                colors = pattern.colors,
                texCoords = pattern.texCoords,
                blendMode = if (effect == null) BlendMode.Dst else BlendMode.Src,
                paint = paint
            )
        }
    }

}

