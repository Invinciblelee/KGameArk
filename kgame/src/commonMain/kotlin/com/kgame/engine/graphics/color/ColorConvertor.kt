package com.kgame.engine.graphics.color

import androidx.compose.ui.graphics.Color

fun Color(hex: String, fallbackColor: Color? = null): Color {
    val cleanHex = hex.removePrefix("#")
    return try {
        when (cleanHex.length) {
            6 -> { // RRGGBB
                Color(
                    red = cleanHex.take(2).toInt(16),
                    green = cleanHex.substring(2, 4).toInt(16),
                    blue = cleanHex.substring(4, 6).toInt(16)
                )
            }
            8 -> { // AARRGGBB
                Color(
                    alpha = cleanHex.take(2).toInt(16),
                    red = cleanHex.substring(2, 4).toInt(16),
                    green = cleanHex.substring(4, 6).toInt(16),
                    blue = cleanHex.substring(6, 8).toInt(16)
                )
            }
            else -> throw IllegalArgumentException("Unsupported hex color: $hex")
        }
    } catch (e: Exception) {
        fallbackColor ?: throw e
    }
}