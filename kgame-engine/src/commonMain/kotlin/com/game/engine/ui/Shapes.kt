package com.game.engine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontVariation.width
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isUnspecified

@Composable
fun Circle(color: Color, radius: Dp = Dp.Unspecified, modifier: Modifier = Modifier) {
    val sizeModifier = if (radius.isUnspecified) Modifier.fillMaxSize() else Modifier.size(radius)
    Canvas(modifier.then(sizeModifier)) {
        drawCircle(color)
    }
}

@Composable
fun Rectangle(color: Color, size: DpSize = DpSize.Unspecified, modifier: Modifier = Modifier) {
    val sizeModifier = if (size.isUnspecified) Modifier.fillMaxSize() else Modifier.size(size)
    Canvas(modifier.then(sizeModifier)) {
        drawRect(color)
    }
}

