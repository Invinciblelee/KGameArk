package com.kgame.plugins.visuals.material

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.MaterialEffect
import com.kgame.plugins.visuals.Visual

open class MaterialVisual(
    val material: Material,
    size: Size = Size.Unspecified,
): Visual(size) {

    constructor(material: Material, size: Float) : this(material, Size(size, size))

    private val effect = MaterialEffect(material)

    override fun update(deltaTime: Float) {
        effect.update(deltaTime)
    }

    final override fun DrawScope.draw() {
        draw(effect.obtainBrush(size))
    }

    open fun DrawScope.draw(brush: Brush) {
        drawRect(brush)
    }

}