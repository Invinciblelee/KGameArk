package com.game.ecs.injectables

import androidx.compose.ui.geometry.Offset

interface CoordinateTransform {
    fun screenToWorld(position: Offset): Offset
    fun worldToScreen(position: Offset): Offset
}