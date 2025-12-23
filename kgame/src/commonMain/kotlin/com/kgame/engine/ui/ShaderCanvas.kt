package com.kgame.engine.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import com.kgame.engine.graphics.shader.Shader
import com.kgame.engine.graphics.shader.ShaderEffect
import com.kgame.engine.math.round
import kotlinx.coroutines.isActive

@Composable
fun ShaderCanvas(
    shader: Shader,
    modifier: Modifier = Modifier,
    onDraw: DrawScope.(Brush) -> Unit,
) {
    val shaderEffect = remember(shader) { ShaderEffect(shader) }

    Canvas(modifier.onSizeChanged {
        shaderEffect.updateResolution(it.toSize())
    }) {
        if (shaderEffect.ready) {
            onDraw(shaderEffect.obtain())
        }
    }
}

@Composable
fun ActiveShaderCanvas(
    shader: Shader,
    speed: Float = 1f,
    modifier: Modifier = Modifier,
    onUpdate: ShaderEffect.() -> Unit = {},
    onDraw: DrawScope.(Brush) -> Unit,
) {
    val shaderEffect = remember(shader) { ShaderEffect(shader) }
    val speedModifier = shader.speedModifier

    var redrawSignal by mutableStateOf(false)

    LaunchedEffect(shaderEffect.supported) {
        if (shaderEffect.supported) {
            var lastFrameMillis = -1L
            while (isActive) {
                onUpdate(shaderEffect)
                withInfiniteAnimationFrameMillis { frameTimeMillis ->
                    if (lastFrameMillis < 0L) {
                        lastFrameMillis = frameTimeMillis
                        return@withInfiniteAnimationFrameMillis
                    }

                    val dt = (frameTimeMillis - lastFrameMillis) / 1000f
                    val safeDt = dt.coerceIn(0f, 0.033f) * speed * speedModifier

                    shaderEffect.updateTime(safeDt)

                    lastFrameMillis = frameTimeMillis
                }
                redrawSignal = !redrawSignal
            }
        }
    }

    Canvas(modifier.onSizeChanged {
        shaderEffect.updateResolution(it.toSize())
    }) {
        redrawSignal

        if (shaderEffect.ready) {
            onDraw(shaderEffect.obtain())
        }
    }
}
