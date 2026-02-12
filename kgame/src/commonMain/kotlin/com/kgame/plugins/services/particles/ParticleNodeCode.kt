package com.kgame.plugins.services.particles

object ParticleNodeCode {
    // Basic Types
    const val SCALAR = 0f
    const val VECTOR2 = 1f
    const val COLOR = 2f

    // Arithmetic
    const val ADD = 10f
    const val SUBTRACT = 11f
    const val MULTIPLY = 12f
    const val DIVIDE = 13f
    const val MOD = 14f
    const val POW = 15f

    // Math & Trig
    const val SIN = 20f
    const val COS = 21f
    const val TAN = 22f
    const val ATAN = 23f
    const val ATAN2 = 24f
    const val ABS = 25f
    const val SQRT = 26f
    const val EXP = 27f
    const val FRACT = 28f
    const val FLOOR = 29f
    const val CEIL = 30f
    const val SIGN = 31f

    // Shaping & Interpolation
    const val MIX = 40f
    const val STEP = 41f
    const val SMOOTH_STEP = 42f
    const val CLAMP = 43f
    const val MAX = 44f
    const val MIN = 45f

    // Vector Logic
    const val DOT = 50f
    const val LENGTH = 51f
    const val NORMALIZE = 52f
    const val DISTANCE = 53f

    // Logic & Random
    const val RANDOM_RANGE = 60f
    const val COMPARISON = 61f
    const val COMBINE = 62f
    const val SELECT = 63f

    // Context Data
    const val TIME = 70f
    const val DELTA_TIME = 71f
    const val INDEX = 72f
    const val COUNT = 73f
    const val PROGRESS = 74f

    const val DUPLICATE = 75f
}