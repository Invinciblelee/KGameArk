package com.kgame.plugins.visuals.material

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.MaterialEffect
import com.kgame.plugins.visuals.Visual

@ExperimentalMaterialVisuals
class MaterialVisual(
    val material: Material,
    size: Size = Size.Unspecified
): Visual(size) {

    constructor(material: Material, size: Float) : this(material, Size(size, size))

    private val effect = MaterialEffect(material)

    override fun update(deltaTime: Float) {
        effect.update(deltaTime)
    }

    override fun DrawScope.draw() {
        drawRect(effect.obtainBrush(size))
    }

}