package com.game.engine.graphics.drawscope

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.drawscope.DrawScope

class ResizedDrawScope(
    private val delegate: DrawScope,
    override val size: Size
) : DrawScope by delegate {

    override val center: Offset = size.center

}