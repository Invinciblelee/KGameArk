package com.game.engine.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isUnspecified
import com.game.engine.graphics.shader.Shader
import com.game.engine.graphics.shader.ShaderEffect
import com.game.engine.math.round
import kotlinx.coroutines.isActive

@Composable
fun ShaderCanvas(
    shader: Shader,
    speed: Float = 1f,
    size: DpSize = DpSize.Unspecified,
    modifier: Modifier = Modifier,
    onDraw: DrawScope.(Brush) -> Unit,
) {
    val sizeModifier = if (size.isUnspecified) Modifier.fillMaxSize() else Modifier.size(size)

    val shaderEffect = remember(shader) { ShaderEffect(shader) }
    val speedModifier = shader.speedModifier

    var time by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(shaderEffect.supported) {
        if (shaderEffect.supported) {
            var startMillis = -1L
            while (isActive) {
                withInfiniteAnimationFrameMillis {
                    if (startMillis < 0) startMillis = it
                    time = ((it - startMillis) / 16.6f) / 10f
                }
            }
        } else {
            time = -1f
        }
    }

    Canvas(modifier.then(sizeModifier)) {
        shaderEffect.update(shader, round(time * speed * speedModifier, 3), this.size)
        if (shaderEffect.ready) {
            onDraw(shaderEffect.build())
        }
    }
}