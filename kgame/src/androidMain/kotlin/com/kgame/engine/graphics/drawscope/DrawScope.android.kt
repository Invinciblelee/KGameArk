package com.kgame.engine.graphics.drawscope

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toAndroidVertexMode

actual fun DrawScope.drawVertices(
    vertexMode: VertexMode,
    positions: FloatArray,
    colors: IntArray?,
    texCoords: FloatArray?,
    indices: ShortArray?,
    blendMode: BlendMode,
    paint: Paint
) {
    drawContext.canvas.nativeCanvas.drawVertices(
        vertexMode.toAndroidVertexMode(),
        positions.size / 2,
        positions, 0,
        texCoords, 0,
        colors, 0,
        indices, 0,
        indices?.size ?: 0,
        paint.asFrameworkPaint()
    )
}