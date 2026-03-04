package com.kgame.engine.math

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.round

fun radians(degrees: Double): Double {
    return degrees * PI / 180.0
}

fun radians(degrees: Float): Float {
    return degrees * PI.toFloat() / 180.0f
}

fun degrees(radians: Double): Double {
    return radians * 180.0 / PI
}

fun degrees(radians: Float): Float {
    return radians * 180.0f / PI.toFloat()
}

fun round(value: Double, precision: Int): Double {
    val factor = 10.0.pow(precision)
    return round(value * factor) / factor
}

fun round(value: Float, precision: Int): Float {
    val factor = 10.0f.pow(precision)
    return round(value * factor) / factor
}

