package com.kgame.plugins.visuals.shapes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import com.kgame.plugins.visuals.Visual

open class CircleVisual(
    var color: Color,
    size: Float,
    val style: DrawStyle = Fill
) : Visual(size) {

    override fun DrawScope.draw() {
        drawCircle(color, style = style, alpha = alpha)
    }

}