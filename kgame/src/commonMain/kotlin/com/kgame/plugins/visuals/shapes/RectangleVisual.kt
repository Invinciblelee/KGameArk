package com.kgame.plugins.visuals.shapes

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import com.kgame.plugins.visuals.Visual

open class RectangleVisual(
    size: Size,
    var color: Color,
    val style: DrawStyle = Fill,
    val cornerRadius: CornerRadius = CornerRadius.Zero,
) : Visual(size) {

    constructor(
        size: Float,
        color: Color,
        style: DrawStyle = Fill,
        cornerRadius: CornerRadius = CornerRadius.Zero
    ): this(Size(size, size), color, style, cornerRadius)

    override fun DrawScope.draw() {
        if (cornerRadius.isZero()) {
            drawRect(color, style = style, alpha = alpha)
        } else {
            drawRoundRect(color, cornerRadius = cornerRadius, style = style, alpha = alpha)
        }
    }
}