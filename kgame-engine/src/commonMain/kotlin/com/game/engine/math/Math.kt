package com.game.engine.math

import kotlin.math.PI

fun toRadians(degrees: Double): Double {
    return degrees * PI / 180.0
}

fun toDegrees(radians: Double): Double {
    return radians * 180.0 / PI
}