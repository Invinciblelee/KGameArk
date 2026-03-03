package com.kgame.engine.graphics.material

import android.os.Build
import androidx.compose.ui.graphics.drawscope.DrawScope

actual fun MaterialEffect(material: Material): MaterialEffect {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AndroidMaterialEffect(material)
    } else {
        DrawScope
        AndroidFallbackMaterialEffect
    }
}

