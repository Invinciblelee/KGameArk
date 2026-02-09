package com.kgame.plugins.visuals.shapes

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import com.kgame.plugins.visuals.Visual

open class RectangleVisual(
    var color: Color,
    size: Size,
    val cornerRadius: CornerRadius = CornerRadius.Zero,
    val style: DrawStyle = Fill,
) : Visual(size) {

    override fun DrawScope.draw() {
        if (cornerRadius.isZero()) {
            drawRect(color, style = style, alpha = alpha)
        } else {
            drawRoundRect(color, cornerRadius = cornerRadius, style = style, alpha = alpha)
        }
    }
}