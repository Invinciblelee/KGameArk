package com.game.engine.math

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2

fun Offset.angle(): Float {
    val rad = atan2(y, x)          // -π .. π
    return (rad * 180f / kotlin.math.PI).toFloat()   // 转角度
}