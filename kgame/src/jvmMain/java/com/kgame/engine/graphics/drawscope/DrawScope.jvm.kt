package com.kgame.engine.graphics.drawscope

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

private val internalPaint = org.jetbrains.skia.Paint().apply {
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
    val skiaVertexMode = when (vertexMode) {
        VertexMode.Triangles -> org.jetbrains.skia.VertexMode.TRIANGLES
        VertexMode.TriangleStrip -> org.jetbrains.skia.VertexMode.TRIANGLE_STRIP
        VertexMode.TriangleFan -> org.jetbrains.skia.VertexMode.TRIANGLE_FAN
        else -> org.jetbrains.skia.VertexMode.TRIANGLES
    }

    val skiaBlendMode = when (blendMode) {
        BlendMode.Clear -> org.jetbrains.skia.BlendMode.CLEAR
        BlendMode.Src -> org.jetbrains.skia.BlendMode.SRC
        BlendMode.Dst -> org.jetbrains.skia.BlendMode.DST
        BlendMode.SrcOver -> org.jetbrains.skia.BlendMode.SRC_OVER
        BlendMode.DstOver -> org.jetbrains.skia.BlendMode.DST_OVER
        BlendMode.SrcIn -> org.jetbrains.skia.BlendMode.SRC_IN
        BlendMode.DstIn -> org.jetbrains.skia.BlendMode.DST_IN
        BlendMode.SrcOut -> org.jetbrains.skia.BlendMode.SRC_OUT
        BlendMode.DstOut -> org.jetbrains.skia.BlendMode.DST_OUT
        BlendMode.SrcAtop -> org.jetbrains.skia.BlendMode.SRC_ATOP
        BlendMode.DstAtop -> org.jetbrains.skia.BlendMode.DST_ATOP
        BlendMode.Xor -> org.jetbrains.skia.BlendMode.XOR
        BlendMode.Plus -> org.jetbrains.skia.BlendMode.PLUS
        BlendMode.Modulate -> org.jetbrains.skia.BlendMode.MODULATE
        BlendMode.Screen -> org.jetbrains.skia.BlendMode.SCREEN
        BlendMode.Overlay -> org.jetbrains.skia.BlendMode.OVERLAY
        BlendMode.Darken -> org.jetbrains.skia.BlendMode.DARKEN
        BlendMode.Lighten -> org.jetbrains.skia.BlendMode.LIGHTEN
        BlendMode.ColorDodge -> org.jetbrains.skia.BlendMode.COLOR_DODGE
        BlendMode.ColorBurn -> org.jetbrains.skia.BlendMode.COLOR_BURN
        BlendMode.Hardlight -> org.jetbrains.skia.BlendMode.HARD_LIGHT
        BlendMode.Softlight -> org.jetbrains.skia.BlendMode.SOFT_LIGHT
        BlendMode.Difference -> org.jetbrains.skia.BlendMode.DIFFERENCE
        BlendMode.Exclusion -> org.jetbrains.skia.BlendMode.EXCLUSION
        BlendMode.Multiply -> org.jetbrains.skia.BlendMode.MULTIPLY
        else -> org.jetbrains.skia.BlendMode.SRC_OVER
    }

    internalPaint.shader = shader
    internalPaint.setAlphaf(alpha)

    drawContext.canvas.nativeCanvas.drawVertices(
        skiaVertexMode,
        positions,
        colors,
        texCoords,
        indices,
        skiaBlendMode,
        internalPaint
    )
}