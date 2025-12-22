package com.kgame.engine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isUnspecified
import com.kgame.engine.graphics.shader.Shader
import com.kgame.engine.graphics.shader.ShaderEffect

@Composable
fun Circle(color: Color, radius: Dp = Dp.Unspecified, modifier: Modifier = Modifier) {
    val sizeModifier = if (radius.isUnspecified) Modifier.fillMaxSize() else Modifier.size(radius)
    Canvas(modifier.then(sizeModifier)) {
        drawCircle(color)
    }
}

@Composable
fun Circle(brush: Brush, radius: Dp = Dp.Unspecified, modifier: Modifier = Modifier) {
    val sizeModifier = if (radius.isUnspecified) Modifier.fillMaxSize() else Modifier.size(radius)
    Canvas(modifier.then(sizeModifier)) {
        drawCircle(brush)
    }
}

@Composable
fun StaticCircle(shader: Shader, radius: Dp = Dp.Unspecified, modifier: Modifier = Modifier) {
    val sizeModifier = if (radius.isUnspecified) Modifier.fillMaxSize() else Modifier.size(radius)
    StaticShaderCanvas(shader, modifier.then(sizeModifier)) {
        drawCircle(it)
    }
}

@Composable
fun ActiveCircle(
    shader: Shader,
    radius: Dp = Dp.Unspecified,
    speed: Float = 1f,
    modifier: Modifier = Modifier,
    onUpdate: ShaderEffect.() -> Unit = {}
) {
    val sizeModifier = if (radius.isUnspecified) Modifier.fillMaxSize() else Modifier.size(radius)
    ActiveShaderCanvas(
        shader = shader,
        speed = speed,
        modifier = modifier.then(sizeModifier),
        onUpdate = onUpdate
    ) {
        drawCircle(it)
    }
}


@Composable
fun Rectangle(color: Color, size: DpSize = DpSize.Unspecified, modifier: Modifier = Modifier) {
    val sizeModifier = if (size.isUnspecified) Modifier.fillMaxSize() else Modifier.size(size)
    Canvas(modifier.then(sizeModifier)) {
        drawRect(color)
    }
}

@Composable
fun Rectangle(brush: Brush, size: DpSize = DpSize.Unspecified, modifier: Modifier = Modifier) {
    val sizeModifier = if (size.isUnspecified) Modifier.fillMaxSize() else Modifier.size(size)
    Canvas(modifier.then(sizeModifier)) {
        drawRect(brush)
    }
}

@Composable
fun Rectangle(shader: Shader, size: DpSize = DpSize.Unspecified, modifier: Modifier = Modifier) {
    val sizeModifier = if (size.isUnspecified) Modifier.fillMaxSize() else Modifier.size(size)
    StaticShaderCanvas(shader, modifier.then(sizeModifier)) {
        drawRect(it)
    }
}

@Composable
fun ActiveRectangle(
    shader: Shader,
    size: DpSize = DpSize.Unspecified,
    speed: Float = 1f,
    modifier: Modifier = Modifier,
    onUpdate: ShaderEffect.() -> Unit = {}
) {
    val sizeModifier = if (size.isUnspecified) Modifier.fillMaxSize() else Modifier.size(size)
    ActiveShaderCanvas(
        shader = shader,
        speed = speed,
        modifier = modifier.then(sizeModifier),
        onUpdate = onUpdate
    ) {
        drawRect(it)
    }
}