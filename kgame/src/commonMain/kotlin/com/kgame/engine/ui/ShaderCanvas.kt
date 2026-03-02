package com.kgame.engine.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.MaterialEffect
import kotlinx.coroutines.isActive


@ExperimentalMaterialVisuals
@Composable
fun MaterialCanvas(
    material: Material,
    modifier: Modifier = Modifier,
    onDraw: DrawScope.(Brush) -> Unit = { drawRect(it) },
) {
    val materialEffect = remember(material) { MaterialEffect(material) }

    Canvas(modifier) {
        onDraw(materialEffect.obtainBrush(size))
    }
}

@ExperimentalMaterialVisuals
@Composable
fun ActiveMaterialCanvas(
    material: Material,
    speed: Float = 1f,
    modifier: Modifier = Modifier,
    onUpdate: MaterialEffect.() -> Unit = {},
    onDraw: DrawScope.(Brush) -> Unit = { drawRect(it) },
) {
    val materialEffect = remember(material) { MaterialEffect(material) }

    val materialState = remember { MaterialState() }

    LaunchedEffect(materialEffect.supported) {
        if (materialEffect.supported) {
            var lastFrameMillis = -1L
            while (isActive) {
                onUpdate(materialEffect)
                withInfiniteAnimationFrameMillis { frameTimeMillis ->
                    if (lastFrameMillis < 0L) {
                        lastFrameMillis = frameTimeMillis
                        return@withInfiniteAnimationFrameMillis
                    }

                    val dt = (frameTimeMillis - lastFrameMillis) / 1000f
                    val safeDt = dt.coerceIn(0f, 0.033f) * speed * material.speedModifier

                    materialEffect.update(safeDt)

                    lastFrameMillis = frameTimeMillis
                }
                materialState.invalidate()
            }
        }
    }

    Canvas(modifier) {
        materialState.signal()

        onDraw(materialEffect.obtainBrush(size))
    }
}


@Stable
class MaterialState {

    private var redrawSignal by mutableStateOf(false)

    fun signal(): Boolean {
        return redrawSignal
    }

    fun invalidate() {
        redrawSignal = !redrawSignal
    }

}