package com.kgame.engine.graphics.drawscope

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toAndroidVertexMode
import androidx.compose.ui.text.TextPainter.paint

private val internalPaint = android.graphics.Paint().apply {
    isAntiAlias = false
}

actual fun DrawScope.drawVertices(
    vertexMode: VertexMode,
    positions: FloatArray,
    colors: IntArray?,
    texCoords: FloatArray?,
    indices: ShortArray?,
    blendMode: BlendMode,
    shader: Shader?,
    alpha: Float
) {
    internalPaint.shader = shader
    internalPaint.alpha = (alpha * 255).toInt()

    drawContext.canvas.nativeCanvas.drawVertices(
        vertexMode.toAndroidVertexMode(),
        positions.size,
        positions, 0,
        texCoords, 0,
        colors, 0,
        indices, 0,
        indices?.size ?: 0,
        internalPaint
    )
}