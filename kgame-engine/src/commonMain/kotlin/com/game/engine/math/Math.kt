package com.game.engine.math

import kotlin.math.PI

fun radians(degrees: Double): Double {
    return degrees * PI / 180.0
}

fun degrees(radians: Double): Double {
    return radians * 180.0 / PI
}